# Circuit breaker

How the outbound circuit breaker works, how far it can be reused, how to use it from your code, and how to deviate from it when a client needs something different.

For the full failure taxonomy, API error contract and test index, see the [README](../README.md).

## In one paragraph

Every `@HttpExchange` client registered through `@ImportHttpServices` gets its own Resilience4j breaker, named after its HTTP service group. `ResilientHttpServiceGroupConfigurer` installs it as a `ClientHttpRequestInterceptor` (`CircuitBreakerInterceptor`) that is always the innermost interceptor. It sees raw statuses and raw `IOException`s, asks the group's `FailureClassifier` what each status means, and records success, failure, or nothing. When the breaker is open, the interceptor throws `CircuitOpenIOException` without touching the wire, and the shared layer turns that into `DependencyFailedException(reason = CIRCUIT_OPEN)`, which the API returns as 502 `DEPENDENCY_FAILED`.

## What the breaker counts

The breaker and the status handler consult the same classifier, so they can't disagree.

| Outcome | Recorded as |
|---|---|
| 2xx | success |
| 4xx business (400, 404, 409, 422, ...) | **success**: the dependency answered |
| 5xx, 408, 429, 1xx, unfollowed 3xx | failure |
| Any `IOException` (timeout, refused, reset, DNS), including a mid-body read failure | failure |
| 401 / 403 | **ignored** (permission released, nothing recorded), unless the client opts in |
| 2xx with an undecodable body | success (decoding happens after the interceptor) |
| Call rejected because the breaker is open | not recorded; never reaches the downstream |

Duration is measured around the request **and** the full body read (`BufferedClientHttpResponse`), so `slowCallDurationThreshold` covers body transfer too.

Every acquired permission is recorded or released exactly once, even on `Error`s. A leaked permission would leave a half-open breaker stuck. `CircuitBreakerInterceptorTest` checks this invariant.

## How reusable is it

### What you get for free

Any client registered in a `RestClient`-backed HTTP service group gets, with zero per-client code:

- a breaker, built from `resilience4j.circuitbreaker.configs.default` unless an `instances.<group>` entry exists,
- the default classifier (`DefaultFailureClassifier.standard()`),
- typed exceptions, one log line and one `downstream.failures` metric per failure,
- breaker health, events and metrics in actuator, created eagerly at startup.

Adding a downstream is one `@ImportHttpServices` line, one `spring.http.serviceclient.<group>` block, and the interface. See "Registration" in the README.

### Granularity: one breaker per group

The breaker is keyed by **group name**, not by interface or method.

- Several interfaces in one `@ImportHttpServices` group share **one** breaker. If a slow endpoint shouldn't trip a fast one, put them in separate groups (each needs its own `spring.http.serviceclient` block).
- There's no per-operation breaker. All methods of a client feed the same sliding window.
- Breakers are isolated across groups: one downstream opening never affects another (scenario 15).

### Where it does not apply

The configurer only implements `RestClientHttpServiceGroupConfigurer`, so the breaker is **not** applied to:

- `RestClient` or `RestTemplate` beans you build by hand,
- `WebClient`-backed (reactive) HTTP service groups,
- anything outside Spring's HTTP Service Registry.

For those, either register them as an HTTP service group, or wrap calls with Resilience4j directly and accept that they won't share the classifier.

### Packaging today

The reusable packages (`client`, `failure`, `call`, `web`) are plain `@Configuration` classes picked up by component scan, and the properties live under the `prototype.clients` prefix. To reuse them in another service today, copy those packages. Extracting them into a Boot starter with auto-configuration is the planned next step. Nothing in them imports `demo`.

## How to inject it

### The normal case: inject the client, not the breaker

You never inject or call the breaker yourself. Constructor-inject the `@HttpExchange` interface and call it; the breaker is already in the request path.

```java
@Service
public class AccountFacade {

    private final AccountClient client;

    public AccountFacade(AccountClient client) {
        this.client = client;
    }

    public Balance balance(String accountId) {
        return client.getBalance(accountId);   // open circuit -> DependencyFailedException(CIRCUIT_OPEN) -> 502
    }
}
```

There's no `@CircuitBreaker` annotation to add and no `try/catch` to write. An open circuit arrives as an ordinary `DependencyFailedException`, the same type as a timeout or a 5xx, so facades don't need to treat it specially. The reason (`CIRCUIT_OPEN`) is only visible in logs and metrics.

If an endpoint has expected business outcomes, use `Downstream.call(...)` (README, "Business failures without try/catch"). It never intercepts `DependencyFailedException`, so an open circuit always propagates.

### When you need the breaker itself

To read state or transition a breaker (an admin endpoint, a degraded-mode check, a test), inject Resilience4j's `CircuitBreakerRegistry` and look it up by group name:

```java
@Component
class NotificationHealth {

    private final CircuitBreakerRegistry registry;

    NotificationHealth(CircuitBreakerRegistry registry) {
        this.registry = registry;
    }

    boolean notificationsAvailable() {
        return registry.circuitBreaker(DemoClients.NOTIFICATION).getState() != CircuitBreaker.State.OPEN;
    }
}
```

The interceptor also looks the breaker up from the registry on every call rather than holding a reference, so `registry.circuitBreaker(name).reset()` and other state transitions take effect immediately.

Don't pass a config when looking up an existing name, and don't create breakers for names that aren't groups. An `instances.<name>` entry that matches no group only logs a WARN at startup.

## Escape hatches

Deviation is opt-in and scoped to one client. You never fork the shared code. Ordered from least to most invasive:

### 1. Tune the breaker (config only)

Override any Resilience4j setting for one client. Unset values inherit from `configs.default`.

```yaml
resilience4j.circuitbreaker.instances:
  account-service:
    baseConfig: default
    slowCallDurationThreshold: 1s
    slowCallRateThreshold: 50
  notification-service:
    baseConfig: default
    failureRateThreshold: 75
```

**Don't** set `recordExceptions`, `ignoreExceptions`, `recordFailurePredicate` or `recordResult`. The interceptor records outcomes explicitly from the classifier, so those settings are either ignored or fight it. Change the classifier instead (hatch 3).

### 2. Turn the breaker off for one client (config only)

```yaml
prototype.clients:
  account-service:
    breaker:
      enabled: false
```

The interceptor isn't installed and no breaker is created. Classification, typed exceptions, logging and metrics are unchanged. Use this for streaming or large-payload clients, because the breaker buffers the whole body in memory. A key under `prototype.clients` that names no group fails startup, so a typo can't silently leave the breaker on.

### 3. Change what counts as a failure: a qualified `FailureClassifier`

```java
@Bean
@Qualifier(DemoClients.NOTIFICATION)
FailureClassifier notificationFailureClassifier() {
    return DefaultFailureClassifier.builder()
            .countRejectedAsFailure(true)                 // 401/403 open this client's breaker
            .override(409, Classification.success())      // duplicate submission = already accepted
            .build();
}
```

- The bean replaces the default for that group only. Two classifiers for one group, or a classifier qualified for no group, fails startup.
- An override changes the breaker **and** the status handler together. Above, a 409 is decoded as a normal response and recorded as a success.
- To make a status count as an outage: `.override(409, Classification.dependencyFailure(FailureReason.SERVER_ERROR))`.
- To make a status neutral to the breaker but still a 502: `Classification.dependencyRejected(BreakerOutcome.IGNORED)`.
- You can implement `FailureClassifier` directly. It must be a pure function of the status.
- I/O failures aren't classified here; they always count as failures.

### 4. Add behaviour around the breaker: a qualified `ResilientClientCustomizer`

```java
@Bean
@Qualifier(DemoClients.NOTIFICATION)
ResilientClientCustomizer notificationApiKey(@Value("${demo.notification.api-key}") String apiKey) {
    return builder -> builder.defaultHeader("X-Api-Key", apiKey);
}
```

- Runs after all defaults, in `@Order` order.
- Interceptors it adds always end up **outside** the breaker; the configurer moves the breaker back to last. So an auth or tracing interceptor's own failures aren't measured by the breaker, and you can't put anything between the breaker and the wire.
- It can't change classification: a `defaultStatusHandler` it adds is never reached. Use hatch 3.
- It must not depend on HTTP service proxies (it runs while the proxy registry is being built).
- For something that applies to every client, use Boot's `RestClientCustomizer`. For static headers, `spring.http.serviceclient.<group>.default-header.*` is enough.

### 5. Operate the breaker at runtime

Through the injected `CircuitBreakerRegistry` (see "When you need the breaker itself"):

| Call | Effect |
|---|---|
| `transitionToForcedOpenState()` | Every call fails fast with `CIRCUIT_OPEN` 502 until changed. Useful to shed a known-bad dependency. |
| `transitionToDisabledState()` | Every call goes through and nothing is recorded, without a restart or config change. |
| `transitionToClosedState()` / `reset()` | Back to normal; `reset()` also clears the sliding window. |

State is per JVM. On multiple instances, each one has to be transitioned separately. `/actuator/circuitbreakers` and `/actuator/circuitbreakerevents` show the result.

### 6. Per-call handling of business outcomes: `Downstream.call(...)`

Not a breaker setting, but listed because it's the right tool when the urge is "catch this one status here". Business outcomes are already breaker successes, so mapping them per call changes nothing about breaker accounting. See the README.

## What you can't do (by design)

- **Per-method breakers.** Split the interface into its own group instead.
- **A global "count 401/403 as failure" property.** Per-client classifier only, so there's one mechanism.
- **Retry inside the breaker.** HttpClient5's automatic retry is disabled on purpose, so one breaker measurement is one attempt. Retry, if added later, belongs outside the breaker and only for idempotent operations.
- **Recording `MALFORMED_RESPONSE` on the breaker.** Decoding happens after the interceptor, so an undecodable 2xx counts as a success. Listed under "Future" in the README.

## Known limitations

- Whole-body buffering suits small JSON APIs only; disable the breaker for large or streaming payloads.
- A half-open probe can be a POST.
- Breaker state is in-memory and per instance.
- HttpClient5's connection-pool lease timeout (default 3 min) isn't exposed by Boot properties and can outlast every breaker-relevant timeout under pool exhaustion.

## Where to look in the code

| Concern | File |
|---|---|
| Installs the breaker, enforces innermost ordering, startup validation | `client/ResilientHttpServiceGroupConfigurer.java` |
| Records outcomes, fails fast when open | `client/CircuitBreakerInterceptor.java` |
| Body buffering inside the measurement | `client/BufferedClientHttpResponse.java` |
| `breaker.enabled` switch | `client/ResilientClientsProperties.java` |
| Status → breaker outcome | `failure/DefaultFailureClassifier.java`, `failure/Classification.java` |
| Open circuit → `CIRCUIT_OPEN` | `failure/CircuitOpenIOException.java`, `failure/DownstreamFailureTranslator.java` |
| Disables HttpClient5 retries | `client/HttpClientConfiguration.java` |
| Example per-client escape hatches | `demo/notification/NotificationClientConfig.java` |
| Breaker behaviour tests | `ResilientRestClientIntegrationTest.CircuitBreakerBehaviour`, `EscapeHatches`, `BreakerDisabledByPropertyIntegrationTest`, `client/CircuitBreakerInterceptorTest` |

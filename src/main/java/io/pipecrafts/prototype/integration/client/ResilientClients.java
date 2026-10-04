package io.pipecrafts.prototype.integration.client;

import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureLog;
import io.pipecrafts.prototype.integration.failure.DownstreamStatusHandler;
import io.pipecrafts.prototype.integration.failure.ErrorBodyParser;
import io.pipecrafts.prototype.integration.failure.FailureClassifier;

/**
 * Applies a listed client's resilience settings to a {@link RestClient.Builder}. The startup instrumentation uses it
 * for bean targets; code uses it directly for a {@code RestClient} that is not a bean:
 * {@code builder.apply(resilientClients.forClient("name"))}.
 *
 * <p>Everything is appended after what the builder already carries, so the project's own interceptors stay
 * outermost; the breaker is always last so it sees raw statuses and raw I/O errors.
 */
public class ResilientClients {

    private final ResilientClientProperties properties;
    private final ClientPolicyResolver policies;
    private final CircuitBreakerRegistry breakerRegistry;
    private final ErrorBodyParser errorBodyParser;
    private final DownstreamFailureLog failureLog;
    private final ObjectProvider<ResilientClientCustomizer> customizers;

    ResilientClients(ResilientClientProperties properties, ClientPolicyResolver policies,
            CircuitBreakerRegistry breakerRegistry, ErrorBodyParser errorBodyParser, DownstreamFailureLog failureLog,
            ObjectProvider<ResilientClientCustomizer> customizers) {
        this.properties = properties;
        this.policies = policies;
        this.breakerRegistry = breakerRegistry;
        this.errorBodyParser = errorBodyParser;
        this.failureLog = failureLog;
        this.customizers = customizers;
    }

    /**
     * @throws IllegalArgumentException if {@code name} is not listed under {@code resilient-client.clients}
     */
    public Consumer<RestClient.Builder> forClient(String name) {
        Map<String, ResilientClientProperties.Client> clients = properties.clients();
        ResilientClientProperties.Client client = clients.get(name);
        if (client == null) {
            throw new IllegalArgumentException("resilient-client.clients.%s is not defined. Known clients: %s"
                    .formatted(name, new TreeSet<>(clients.keySet())));
        }
        if (!properties.enabled() || !client.enabled()) {
            return builder -> {
            };
        }
        FailureClassifier policy = policies.resolve(name);
        return builder -> apply(name, client, policy, builder);
    }

    private void apply(String name, ResilientClientProperties.Client client, FailureClassifier policy,
            RestClient.Builder builder) {
        if (client.errorHandling().enabled()) {
            // Matches every status so Spring's default 4xx/5xx handler is never reached.
            builder.defaultStatusHandler(status -> true,
                    new DownstreamStatusHandler(name, policy, errorBodyParser, failureLog));
            builder.requestInterceptor(new DownstreamFailureInterceptor(name, failureLog));
        }
        customizers.orderedStream().forEach(customizer -> customizer.customize(name, builder));
        if (client.circuitBreaker().enabled()) {
            builder.requestInterceptors(interceptors -> {
                if (interceptors.stream().anyMatch(CircuitBreakerInterceptor.class::isInstance)) {
                    throw new IllegalStateException(
                            "resilient-client.clients.%s: breaker configured twice (YAML and code). Remove one."
                                    .formatted(name));
                }
            });
            builder.requestInterceptor(new CircuitBreakerInterceptor(name, breakerRegistry, policy));
            // Created eagerly so actuator lists every client from startup, not only after its first call.
            breakerRegistry.circuitBreaker(name);
        }
    }
}

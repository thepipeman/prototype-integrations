package io.pipecrafts.prototype.integration.client;

import java.util.function.Consumer;

import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.pipecrafts.prototype.integration.failure.DefaultFailureClassifier;
import io.pipecrafts.prototype.integration.failure.FailureClassifier;

/**
 * Adds a Resilience4j circuit breaker to a {@link RestClient} the project already builds:
 * {@code builder.apply(circuitBreakers.forClient("account-service"))}.
 *
 * <p>Apply it last, after the project's own interceptors, so the breaker is innermost and measures raw statuses and
 * raw I/O errors. It adds nothing else: no base URL, timeouts, status handling or exception translation. An open
 * circuit surfaces as a {@code ResourceAccessException} caused by a {@code CircuitOpenIOException}.
 */
public final class RestClientCircuitBreakers {

    private final CircuitBreakerRegistry registry;
    private final ResilientClientProperties properties;

    public RestClientCircuitBreakers(CircuitBreakerRegistry registry, ResilientClientProperties properties) {
        this.registry = registry;
        this.properties = properties;
    }

    /** Breaker named {@code name} with the default failure taxonomy; tuned by {@code resilience4j.circuitbreaker.instances.<name>}. */
    public Consumer<RestClient.Builder> forClient(String name) {
        return forClient(name, DefaultFailureClassifier.standard());
    }

    /** Same, with a per-client classifier (for example, 409 counted as success). */
    public Consumer<RestClient.Builder> forClient(String name, FailureClassifier classifier) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Circuit breaker name must not be blank");
        }
        if (!properties.breakerEnabled(name)) {
            return builder -> {
            };
        }
        // Created eagerly so actuator shows every client from startup, not only after its first call.
        registry.circuitBreaker(name);
        return builder -> builder.requestInterceptor(new CircuitBreakerInterceptor(name, registry, classifier));
    }
}

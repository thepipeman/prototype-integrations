package io.pipecrafts.prototype.integration.client;

import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The downstreams to protect, keyed by client name. The name is also the circuit breaker instance name and the
 * {@code dependency} tag in logs and metrics. Breaker tuning stays under the standard
 * {@code resilience4j.circuitbreaker.instances.<name>}.
 *
 * @param enabled global kill switch; {@code false} makes the library do nothing at all
 */
@ConfigurationProperties("resilient-client")
public record ResilientClientProperties(@DefaultValue("true") boolean enabled,
        @DefaultValue Map<String, Client> clients) {

    public ResilientClientProperties {
        clients = Map.copyOf(clients);
    }

    /**
     * @param enabled {@code false} keeps the entry but skips it
     * @param bean the {@code RestClient} bean to instrument; defaults to the client name
     * @param httpServiceGroup alternative target: a Spring HTTP service group; exclusive with {@code bean}
     */
    public record Client(@DefaultValue("true") boolean enabled, @Nullable String bean,
            @Nullable String httpServiceGroup, @DefaultValue CircuitBreaker circuitBreaker,
            @DefaultValue ErrorHandling errorHandling, @DefaultValue Policy policy) {

        public String beanName(String clientName) {
            return bean != null ? bean : clientName;
        }
    }

    public record CircuitBreaker(@DefaultValue("true") boolean enabled) {
    }

    /** {@code enabled: true} installs the status handler and I/O translation, which changes the exceptions callers see. */
    public record ErrorHandling(@DefaultValue("false") boolean enabled) {
    }

    /**
     * The failure taxonomy for one client, shared by the breaker and the status handler.
     *
     * @param countRejectedAsFailure whether 401/403 are recorded as breaker failures rather than ignored
     */
    public record Policy(@DefaultValue("false") boolean countRejectedAsFailure,
            @DefaultValue Map<Integer, Outcome> statusOverrides) {

        public Policy {
            statusOverrides = Map.copyOf(statusOverrides);
            statusOverrides.keySet().forEach(status -> {
                if (status < 100 || status > 599) {
                    throw new IllegalArgumentException("status-overrides key must be an HTTP status (100-599): " + status);
                }
            });
        }

        /** Whether the YAML deviates from the default taxonomy; absent and default policies are indistinguishable. */
        public boolean isCustom() {
            return countRejectedAsFailure || !statusOverrides.isEmpty();
        }
    }

    public enum Outcome {
        SUCCESS, BUSINESS, DEPENDENCY_FAILURE, DEPENDENCY_REJECTED
    }
}

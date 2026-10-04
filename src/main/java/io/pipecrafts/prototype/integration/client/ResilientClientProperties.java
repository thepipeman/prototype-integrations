package io.pipecrafts.prototype.integration.client;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Per-client switches keyed by breaker name; breaker tuning lives under {@code resilience4j.circuitbreaker}. */
@ConfigurationProperties("resilient-client")
public record ResilientClientProperties(@DefaultValue Map<String, Breaker> circuitBreaker) {

    public ResilientClientProperties {
        circuitBreaker = Map.copyOf(circuitBreaker);
    }

    public boolean breakerEnabled(String name) {
        Breaker breaker = circuitBreaker.get(name);
        return breaker == null || breaker.enabled();
    }

    /** {@code enabled: false} removes the interceptor entirely; classification and logging are unaffected. */
    public record Breaker(@DefaultValue("true") boolean enabled) {
    }
}

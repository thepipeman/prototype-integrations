package io.pipecrafts.prototype.integration.client;

import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Resilience settings per HTTP service group, keyed by group name. Transport settings (base URL, timeouts, default
 * headers) live under Boot's {@code spring.http.serviceclient.<group>} instead.
 */
@ConfigurationProperties("prototype")
public record ResilientClientsProperties(@DefaultValue Map<String, Client> clients) {

    public ResilientClientsProperties {
        clients = Map.copyOf(clients);
    }

    public boolean breakerEnabled(String group) {
        Client client = clients.get(group);
        return client == null || client.breaker().enabled();
    }

    public record Client(@DefaultValue Breaker breaker) {
    }

    /** {@code enabled: false} removes the interceptor entirely; classification and logging are unaffected. */
    public record Breaker(@DefaultValue("true") boolean enabled) {
    }
}

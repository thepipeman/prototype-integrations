package io.pipecrafts.prototype.integration.demo;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Transport settings of each demo downstream, keyed by client name. */
@ConfigurationProperties("demo")
public record DemoClientsProperties(@DefaultValue Map<String, Endpoint> clients) {

    public DemoClientsProperties {
        clients = Map.copyOf(clients);
    }

    public Endpoint get(String name) {
        Endpoint endpoint = clients.get(name);
        if (endpoint == null) {
            throw new IllegalStateException("No demo.clients." + name + " configured");
        }
        return endpoint;
    }

    public record Endpoint(String baseUrl, @DefaultValue("500ms") Duration connectTimeout,
            @DefaultValue("2s") Duration readTimeout, @DefaultValue Map<String, String> defaultHeader) {
    }
}

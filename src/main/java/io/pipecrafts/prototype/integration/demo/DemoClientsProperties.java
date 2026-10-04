package io.pipecrafts.prototype.integration.demo;

import java.time.Duration;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

/** Transport settings per downstream, keyed by client name. */
@ConfigurationProperties("demo")
public record DemoClientsProperties(@DefaultValue Map<String, Client> clients) {

    public DemoClientsProperties {
        clients = Map.copyOf(clients);
    }

    public Client client(String name) {
        Client client = clients.get(name);
        if (client == null || !StringUtils.hasText(client.baseUrl())) {
            throw new IllegalStateException("Downstream '%s' has no demo.clients.%s.base-url".formatted(name, name));
        }
        return client;
    }

    public record Client(String baseUrl, @DefaultValue("500ms") Duration connectTimeout,
            @DefaultValue("2s") Duration readTimeout, @DefaultValue Map<String, String> defaultHeader) {
    }
}

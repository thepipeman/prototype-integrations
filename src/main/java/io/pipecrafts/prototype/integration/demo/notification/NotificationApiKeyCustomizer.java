package io.pipecrafts.prototype.integration.demo.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.pipecrafts.prototype.integration.client.ResilientClientCustomizer;
import io.pipecrafts.prototype.integration.demo.DemoClients;

/** Adds the notification API key while the client is instrumented; other clients are left alone. */
@Configuration(proxyBeanMethods = false)
class NotificationApiKeyCustomizer {

    @Bean
    ResilientClientCustomizer notificationApiKey(@Value("${demo.notification.api-key}") String apiKey) {
        return (name, builder) -> {
            if (name.equals(DemoClients.NOTIFICATION)) {
                builder.defaultHeader("X-Api-Key", apiKey);
            }
        };
    }
}

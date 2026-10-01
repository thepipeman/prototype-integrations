package io.pipecrafts.prototype.integration.demo.notification;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.pipecrafts.prototype.integration.client.ResilientClientCustomizer;
import io.pipecrafts.prototype.integration.demo.DemoClients;
import io.pipecrafts.prototype.integration.failure.Classification;
import io.pipecrafts.prototype.integration.failure.DefaultFailureClassifier;
import io.pipecrafts.prototype.integration.failure.FailureClassifier;

/** Per-client escape hatches for the notification downstream; no other client is affected. */
@Configuration(proxyBeanMethods = false)
public class NotificationClientConfig {

    /**
     * A 409 means "duplicate submission, already accepted" and carries a normal receipt. Rejected credentials
     * count as breaker failures: a non-critical dependency with broken auth should stop being called.
     */
    @Bean
    @Qualifier(DemoClients.NOTIFICATION)
    FailureClassifier notificationFailureClassifier() {
        return DefaultFailureClassifier.builder()
                .countRejectedAsFailure(true)
                .override(409, Classification.success())
                .build();
    }

    @Bean
    @Qualifier(DemoClients.NOTIFICATION)
    ResilientClientCustomizer notificationApiKey(@Value("${demo.notification.api-key}") String apiKey) {
        return builder -> builder.defaultHeader("X-Api-Key", apiKey);
    }
}

package io.pipecrafts.prototype.integration.demo.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.pipecrafts.prototype.integration.client.ResilientRestClients;
import io.pipecrafts.prototype.integration.demo.DemoClients;
import io.pipecrafts.prototype.integration.failure.Classification;
import io.pipecrafts.prototype.integration.failure.DefaultFailureClassifier;

/** Escape-hatch showcase: its own failure policy and an extra header, without touching any other client. */
@Component
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(ResilientRestClients clients, @Value("${demo.notification.api-key}") String apiKey) {
        this.restClient = clients.create(DemoClients.NOTIFICATION,
                // A 409 means "duplicate submission, already accepted" and carries a normal receipt. Rejected
                // credentials count as breaker failures: a non-critical dependency with broken auth should stop
                // being called.
                DefaultFailureClassifier.builder()
                        .countRejectedAsFailure(true)
                        .override(409, Classification.success())
                        .build(),
                builder -> builder.defaultHeader("X-Api-Key", apiKey));
    }

    public NotificationReceipt send(NotificationRequest request) {
        return restClient.post().uri("/notifications").body(request).retrieve().body(NotificationReceipt.class);
    }

    public NotificationStatus getStatus(String id) {
        return restClient.get().uri("/notifications/{id}/status", id).retrieve().body(NotificationStatus.class);
    }
}

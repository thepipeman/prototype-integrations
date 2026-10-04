package io.pipecrafts.prototype.integration.demo.notification;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class NotificationClient {

    private final RestClient restClient;

    public NotificationClient(@Qualifier("notificationRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public NotificationReceipt send(NotificationRequest request) {
        return restClient.post().uri("/notifications").body(request).retrieve().body(NotificationReceipt.class);
    }

    public NotificationStatus getStatus(String id) {
        return restClient.get().uri("/notifications/{id}/status", id).retrieve().body(NotificationStatus.class);
    }
}

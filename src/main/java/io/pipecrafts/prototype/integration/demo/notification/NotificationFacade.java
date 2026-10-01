package io.pipecrafts.prototype.integration.demo.notification;

import org.springframework.stereotype.Service;

@Service
public class NotificationFacade {

    private final NotificationClient client;

    public NotificationFacade(NotificationClient client) {
        this.client = client;
    }

    public NotificationReceipt send(NotificationRequest request) {
        return client.send(request);
    }

    public NotificationStatus status(String notificationId) {
        return client.getStatus(notificationId);
    }
}

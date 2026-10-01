package io.pipecrafts.prototype.integration.demo.notification;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/notifications")
public interface NotificationClient {

    @PostExchange
    NotificationReceipt send(@RequestBody NotificationRequest request);

    @GetExchange("/{id}/status")
    NotificationStatus getStatus(@PathVariable String id);
}

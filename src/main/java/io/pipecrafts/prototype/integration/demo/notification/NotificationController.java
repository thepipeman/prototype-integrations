package io.pipecrafts.prototype.integration.demo.notification;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/demo/notifications")
public class NotificationController {

    private final NotificationFacade facade;

    public NotificationController(NotificationFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    public NotificationReceipt send(@Valid @RequestBody NotificationRequest request) {
        return facade.send(request);
    }

    @GetMapping("/{notificationId}/status")
    public NotificationStatus status(@PathVariable @Size(max = 64) String notificationId) {
        return facade.status(notificationId);
    }
}

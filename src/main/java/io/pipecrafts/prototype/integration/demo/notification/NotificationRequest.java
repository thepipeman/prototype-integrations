package io.pipecrafts.prototype.integration.demo.notification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record NotificationRequest(@NotBlank @Size(max = 64) String recipient, @NotBlank @Size(max = 16) String channel,
        @NotBlank @Size(max = 500) String message) {
}

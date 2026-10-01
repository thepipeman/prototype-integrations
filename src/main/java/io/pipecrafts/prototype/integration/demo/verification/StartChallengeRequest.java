package io.pipecrafts.prototype.integration.demo.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record StartChallengeRequest(@NotBlank @Size(max = 64) String customerId, @NotBlank @Size(max = 16) String channel) {
}

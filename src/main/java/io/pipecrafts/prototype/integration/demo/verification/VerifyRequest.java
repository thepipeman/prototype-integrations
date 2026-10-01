package io.pipecrafts.prototype.integration.demo.verification;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VerifyRequest(@NotBlank @Size(max = 12) String code) {
}

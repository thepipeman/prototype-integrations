package io.pipecrafts.prototype.integration.demo.verification;

import java.time.Instant;

public record Challenge(String challengeId, String channel, Instant expiresAt) {
}

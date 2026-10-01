package io.pipecrafts.prototype.integration.failure;

/** Internal reason for a dependency failure; appears in logs and metrics only, never in API responses. */
public enum FailureReason {
    SERVER_ERROR,
    TIMEOUT,
    CONNECTION_FAILED,
    CIRCUIT_OPEN,
    UNAUTHORIZED,
    THROTTLED,
    MALFORMED_RESPONSE
}

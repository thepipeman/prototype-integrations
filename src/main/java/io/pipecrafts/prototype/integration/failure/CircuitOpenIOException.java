package io.pipecrafts.prototype.integration.failure;

import java.io.IOException;

/**
 * Thrown by the circuit breaker interceptor instead of contacting the downstream.
 *
 * <p>An {@link IOException} on purpose: it travels the same path as a connection failure and is translated into
 * {@code DependencyFailedException(reason = CIRCUIT_OPEN)} by the shared layer.
 */
public final class CircuitOpenIOException extends IOException {

    public CircuitOpenIOException(String dependency) {
        super("Circuit breaker '" + dependency + "' does not permit calls");
    }
}

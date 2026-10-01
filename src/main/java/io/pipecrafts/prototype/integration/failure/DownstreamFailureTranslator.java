package io.pipecrafts.prototype.integration.failure;

import java.io.IOException;
import java.io.InterruptedIOException;

import org.jspecify.annotations.Nullable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

/**
 * Maps whatever {@code RestClient} threw onto the sealed {@link DownstreamException} hierarchy.
 *
 * <p>Anything that is not a client failure passes through untouched, so a bug becomes a 500 rather than a
 * disguised 502.
 */
public final class DownstreamFailureTranslator {

    private static final int MAX_CAUSE_DEPTH = 16;

    private DownstreamFailureTranslator() {
    }

    public static RuntimeException translate(RuntimeException ex, String dependency, String operation) {
        return switch (ex) {
            case DownstreamException downstream -> downstream;
            case ResourceAccessException access ->
                    DependencyFailedException.ofTransport(dependency, operation, ioReason(access), access);
            case RestClientException client -> {
                // An I/O error while decoding only happens when the body was not buffered (breaker disabled).
                FailureReason reason = findIoException(client) != null ? ioReason(client)
                        : FailureReason.MALFORMED_RESPONSE;
                yield DependencyFailedException.ofTransport(dependency, operation, reason, client);
            }
            default -> ex;
        };
    }

    static FailureReason ioReason(Throwable failure) {
        boolean timeout = false;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof CircuitOpenIOException) {
                return FailureReason.CIRCUIT_OPEN;
            }
            timeout |= current instanceof InterruptedIOException;
            current = nextCause(current);
        }
        return timeout ? FailureReason.TIMEOUT : FailureReason.CONNECTION_FAILED;
    }

    private static @Nullable IOException findIoException(Throwable failure) {
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof IOException io) {
                return io;
            }
            current = nextCause(current);
        }
        return null;
    }

    private static @Nullable Throwable nextCause(Throwable current) {
        Throwable cause = current.getCause();
        return cause == current ? null : cause;
    }
}

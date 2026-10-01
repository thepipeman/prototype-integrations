package io.pipecrafts.prototype.integration.failure;

import java.io.InterruptedIOException;

/** Picks the {@link FailureReason} for an I/O failure from its cause chain. */
public final class DownstreamFailureTranslator {

    private static final int MAX_CAUSE_DEPTH = 16;

    private DownstreamFailureTranslator() {
    }

    public static FailureReason ioReason(Throwable failure) {
        boolean timeout = false;
        Throwable current = failure;
        for (int depth = 0; current != null && depth < MAX_CAUSE_DEPTH; depth++) {
            if (current instanceof CircuitOpenIOException) {
                return FailureReason.CIRCUIT_OPEN;
            }
            timeout |= current instanceof InterruptedIOException;
            current = current.getCause() == current ? null : current.getCause();
        }
        return timeout ? FailureReason.TIMEOUT : FailureReason.CONNECTION_FAILED;
    }
}

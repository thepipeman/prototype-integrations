package io.pipecrafts.prototype.integration.failure;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;
import org.slf4j.spi.LoggingEventBuilder;

import io.micrometer.core.instrument.MeterRegistry;

/**
 * The only place downstream failures are logged and counted, so facades never log them again.
 *
 * <p>Logs operation names, never URLs or query strings (they can carry PII), and never headers. The downstream
 * error message and a truncated body appear at DEBUG only.
 */
public final class DownstreamFailureLog {

    public static final String METRIC = "downstream.failures";

    private static final Logger log = LoggerFactory.getLogger(DownstreamFailureLog.class);

    private final @Nullable MeterRegistry meterRegistry;

    /** A {@code null} registry logs without counting. */
    public DownstreamFailureLog(@Nullable MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    public void record(DownstreamException failure) {
        String category = failure.category().name();
        String reason = failure instanceof DependencyFailedException dependency ? dependency.reason().name() : "none";
        String status = failure.httpStatus().map(code -> String.valueOf(code.value())).orElse("none");
        DownstreamError error = failure instanceof DownstreamBusinessException business ? business.error()
                : DownstreamError.empty();
        String downstreamCode = error.code() == null ? "none" : error.code();
        String cause = rootCauseType(failure);

        LoggingEventBuilder event = log.atLevel(failure.category() == FailureCategory.BUSINESS ? Level.INFO : Level.WARN)
                .addKeyValue("dependency", failure.dependency())
                .addKeyValue("operation", failure.operation())
                .addKeyValue("outcome", category)
                .addKeyValue("reason", reason)
                .addKeyValue("httpStatus", status)
                .addKeyValue("downstreamCode", downstreamCode);
        if (cause != null) {
            event = event.addKeyValue("cause", cause);
        }
        event.log("Downstream call failed: dependency={} operation={} outcome={} reason={} httpStatus={} "
                        + "downstreamCode={}{}", failure.dependency(), failure.operation(), category, reason, status,
                downstreamCode, cause == null ? "" : " cause=" + cause);

        if (log.isDebugEnabled()) {
            log.debug("Downstream failure detail: dependency={} operation={} downstreamMessage={} body={}",
                    failure.dependency(), failure.operation(), error.message(), failure.responseSnippet());
        }

        if (meterRegistry != null) {
            meterRegistry.counter(METRIC,
                            "dependency", failure.dependency(),
                            "operation", failure.operation(),
                            "category", category,
                            "reason", reason)
                    .increment();
        }
    }

    /** The exception type only: transport exception messages carry the request URL. */
    private static @Nullable String rootCauseType(Throwable failure) {
        Throwable root = null;
        Throwable current = failure.getCause();
        for (int depth = 0; current != null && depth < 16; depth++) {
            root = current;
            current = current.getCause() == current ? null : current.getCause();
        }
        return root == null ? null : root.getClass().getSimpleName();
    }
}

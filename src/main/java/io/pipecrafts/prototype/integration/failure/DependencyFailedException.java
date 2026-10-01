package io.pipecrafts.prototype.integration.failure;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;

/** The dependency could not serve us (outage, timeout, throttling, open circuit, rejected credentials). */
public final class DependencyFailedException extends DownstreamException {

    private final FailureCategory category;
    private final FailureReason reason;

    public DependencyFailedException(String dependency, String operation, @Nullable HttpStatusCode httpStatus,
            FailureCategory category, FailureReason reason, @Nullable String responseSnippet,
            @Nullable Throwable cause) {
        super(dependency, operation, httpStatus, responseSnippet,
                describe(dependency, operation, category, reason, httpStatus), cause);
        if (category != FailureCategory.DEPENDENCY_FAILURE && category != FailureCategory.DEPENDENCY_REJECTED) {
            throw new IllegalArgumentException("Not a dependency category: " + category);
        }
        this.category = category;
        this.reason = requireNonNull(reason, "reason");
    }

    /** For failures with no HTTP response: I/O errors, open circuit, undecodable body. */
    public static DependencyFailedException ofTransport(String dependency, String operation, FailureReason reason,
            Throwable cause) {
        return new DependencyFailedException(dependency, operation, null, FailureCategory.DEPENDENCY_FAILURE, reason,
                null, cause);
    }

    @Override
    public FailureCategory category() {
        return category;
    }

    public FailureReason reason() {
        return reason;
    }
}

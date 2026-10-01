package io.pipecrafts.prototype.integration.failure;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;

/** The dependency is healthy and rejected the request; callers may map it to a domain outcome. */
public final class DownstreamBusinessException extends DownstreamException {

    private final HttpStatusCode status;
    private final DownstreamError error;

    public DownstreamBusinessException(String dependency, String operation, HttpStatusCode status,
            DownstreamError error, @Nullable String responseSnippet) {
        super(dependency, operation, requireNonNull(status, "status"), responseSnippet,
                describe(dependency, operation, FailureCategory.BUSINESS, null, status), null);
        this.status = status;
        this.error = requireNonNull(error, "error");
    }

    @Override
    public FailureCategory category() {
        return FailureCategory.BUSINESS;
    }

    public HttpStatusCode status() {
        return status;
    }

    public DownstreamError error() {
        return error;
    }
}

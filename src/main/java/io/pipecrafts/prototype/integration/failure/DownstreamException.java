package io.pipecrafts.prototype.integration.failure;

import java.util.Optional;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatusCode;

/**
 * A classified outbound-call failure. The message is built only from dependency, operation, category, reason and
 * status; bodies, headers and URLs never appear in it because messages end up in logs and stack traces.
 */
public abstract sealed class DownstreamException extends RuntimeException
        permits DependencyFailedException, DownstreamBusinessException {

    private static final int SNIPPET_LIMIT = 512;

    private final String dependency;
    private final String operation;
    private final @Nullable HttpStatusCode httpStatus;
    private final @Nullable String responseSnippet;

    DownstreamException(String dependency, String operation, @Nullable HttpStatusCode httpStatus,
            @Nullable String responseSnippet, String message, @Nullable Throwable cause) {
        super(message, cause);
        this.dependency = dependency;
        this.operation = operation;
        this.httpStatus = httpStatus;
        this.responseSnippet = truncate(responseSnippet);
    }

    /** The HTTP service group (client) name. */
    public String dependency() {
        return dependency;
    }

    /** The call as {@code METHOD /uri/{template}}; see {@link DownstreamOperation}. */
    public String operation() {
        return operation;
    }

    /** Empty for failures without a response (I/O errors, open circuit). */
    public Optional<HttpStatusCode> httpStatus() {
        return Optional.ofNullable(httpStatus);
    }

    public abstract FailureCategory category();

    /** Truncated raw response body; package-private so only the failure log can reach it, at DEBUG. */
    @Nullable String responseSnippet() {
        return responseSnippet;
    }

    static String describe(String dependency, String operation, FailureCategory category,
            @Nullable FailureReason reason, @Nullable HttpStatusCode status) {
        return "%s %s: category=%s reason=%s httpStatus=%s".formatted(
                dependency, operation, category, reason == null ? "none" : reason,
                status == null ? "none" : status.value());
    }

    private static @Nullable String truncate(@Nullable String snippet) {
        if (snippet == null || snippet.isEmpty()) {
            return null;
        }
        return snippet.length() <= SNIPPET_LIMIT ? snippet : snippet.substring(0, SNIPPET_LIMIT) + "...";
    }
}

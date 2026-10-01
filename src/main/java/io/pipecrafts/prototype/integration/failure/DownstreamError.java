package io.pipecrafts.prototype.integration.failure;

import org.jspecify.annotations.Nullable;

/** The parsed downstream error body. Both fields are null when the body is missing or not the expected shape. */
public record DownstreamError(@Nullable String code, @Nullable String message) {

    private static final DownstreamError EMPTY = new DownstreamError(null, null);

    public static DownstreamError empty() {
        return EMPTY;
    }
}

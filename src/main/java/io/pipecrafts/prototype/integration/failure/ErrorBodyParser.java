package io.pipecrafts.prototype.integration.failure;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

/** Extracts a {@link DownstreamError} from an error response body. Implementations must never throw. */
@FunctionalInterface
public interface ErrorBodyParser {

    DownstreamError parse(byte[] body, @Nullable MediaType contentType);
}

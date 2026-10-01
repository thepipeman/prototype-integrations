package io.pipecrafts.prototype.integration.failure;

import org.jspecify.annotations.Nullable;
import org.springframework.http.MediaType;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads {@code {"code": "...", "message": "..."}} and degrades to {@link DownstreamError#empty()} for anything else.
 *
 * <p>The content type is deliberately ignored: downstreams often send JSON labelled {@code text/plain}, and a
 * failed parse is harmless.
 */
public final class TolerantErrorBodyParser implements ErrorBodyParser {

    public static final int MAX_BODY_BYTES = 16 * 1024;

    private final JsonMapper jsonMapper;

    public TolerantErrorBodyParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public DownstreamError parse(byte[] body, @Nullable MediaType contentType) {
        if (body.length == 0 || body.length > MAX_BODY_BYTES) {
            return DownstreamError.empty();
        }
        try {
            JsonNode root = jsonMapper.readTree(body);
            if (root == null || !root.isObject()) {
                return DownstreamError.empty();
            }
            return new DownstreamError(text(root, "code"), text(root, "message"));
        }
        catch (Exception ex) {
            return DownstreamError.empty();
        }
    }

    private static @Nullable String text(JsonNode root, String field) {
        JsonNode node = root.get(field);
        if (node == null || !node.isString()) {
            return null;
        }
        String value = node.stringValue();
        return value.isBlank() ? null : value;
    }
}

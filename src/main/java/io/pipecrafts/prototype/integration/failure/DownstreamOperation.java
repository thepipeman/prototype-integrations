package io.pipecrafts.prototype.integration.failure;

import java.util.Map;

/** Request attribute carrying the {@code Interface#method} name from the HTTP service proxy down to the handler. */
public final class DownstreamOperation {

    public static final String ATTRIBUTE = "downstream.operation";

    private static final String UNKNOWN = "unknown";

    private DownstreamOperation() {
    }

    public static String from(Map<String, Object> attributes) {
        return attributes.get(ATTRIBUTE) instanceof String operation ? operation : UNKNOWN;
    }
}

package io.pipecrafts.prototype.integration.failure;

import org.springframework.http.HttpRequest;

/**
 * Names a call as {@code METHOD /uri/{template}}: bounded and PII-free, so safe for logs and metric tags.
 *
 * <p>RestClient records the template when a call uses {@code uri(String template, ...)}; a call built from a
 * {@code URI} or a {@code UriBuilder} function alone has no template and is named {@code unknown}.
 */
public final class DownstreamOperation {

    /** Set by RestClient itself; mirrors the private {@code DefaultRestClient.URI_TEMPLATE_ATTRIBUTE}. */
    static final String URI_TEMPLATE_ATTRIBUTE = "org.springframework.web.client.RestClient.uriTemplate";

    private DownstreamOperation() {
    }

    public static String of(HttpRequest request) {
        String path = request.getAttributes().get(URI_TEMPLATE_ATTRIBUTE) instanceof String template
                ? pathOf(template)
                : "unknown";
        return request.getMethod().name() + " " + path;
    }

    /** The template includes the base URL; keep only the path and drop the query. */
    private static String pathOf(String template) {
        int scheme = template.indexOf("://");
        int start = scheme < 0 ? 0 : template.indexOf('/', scheme + 3);
        if (start < 0) {
            return "/";
        }
        int query = template.indexOf('?', start);
        return query < 0 ? template.substring(start) : template.substring(start, query);
    }
}

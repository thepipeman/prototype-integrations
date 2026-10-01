package io.pipecrafts.prototype.integration.client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;

/** A fully read response, so body-transfer time and body-read failures fall inside the breaker's measurement. */
final class BufferedClientHttpResponse implements ClientHttpResponse {

    private final HttpStatusCode statusCode;
    private final String statusText;
    private final HttpHeaders headers;
    private final byte[] body;

    private BufferedClientHttpResponse(HttpStatusCode statusCode, String statusText, HttpHeaders headers,
            byte[] body) {
        this.statusCode = statusCode;
        this.statusText = statusText;
        this.headers = headers;
        this.body = body;
    }

    static BufferedClientHttpResponse drain(ClientHttpResponse response) throws IOException {
        try (response; InputStream in = response.getBody()) {
            return new BufferedClientHttpResponse(response.getStatusCode(), response.getStatusText(),
                    HttpHeaders.copyOf(response.getHeaders()), in.readAllBytes());
        }
    }

    @Override
    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    @Override
    public String getStatusText() {
        return statusText;
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }

    @Override
    public InputStream getBody() {
        return new ByteArrayInputStream(body);
    }

    @Override
    public void close() {
    }
}

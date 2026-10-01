package io.pipecrafts.prototype.integration.failure;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;

import java.io.IOException;
import java.io.InputStream;

import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestClient;

/**
 * Turns every non-success classification into a typed {@link DownstreamException}, logging it once.
 *
 * <p>Registered for every status, so it pre-empts Spring's default 4xx/5xx handler: a classifier that maps a 4xx
 * to success really does get a normal decode.
 */
public final class DownstreamStatusHandler implements RestClient.ResponseSpec.ErrorHandler {

    private final String dependency;
    private final FailureClassifier classifier;
    private final ErrorBodyParser parser;
    private final DownstreamFailureLog failureLog;

    public DownstreamStatusHandler(String dependency, FailureClassifier classifier, ErrorBodyParser parser,
            DownstreamFailureLog failureLog) {
        this.dependency = dependency;
        this.classifier = classifier;
        this.parser = parser;
        this.failureLog = failureLog;
    }

    @Override
    public void handle(HttpRequest request, ClientHttpResponse response) throws IOException {
        HttpStatusCode status = response.getStatusCode();
        Classification classification = classifier.classify(status);
        String operation = DownstreamOperation.of(request);
        @Nullable DownstreamException failure = switch (classification.category()) {
            case SUCCESS -> null;
            case BUSINESS -> {
                byte[] body = readBody(response);
                yield new DownstreamBusinessException(dependency, operation, status,
                        parser.parse(body, contentType(response)), new String(body, UTF_8));
            }
            case DEPENDENCY_FAILURE, DEPENDENCY_REJECTED -> new DependencyFailedException(dependency, operation,
                    status, classification.category(), requireNonNull(classification.reason()),
                    new String(readBody(response), UTF_8), null);
        };
        if (failure != null) {
            failureLog.record(failure);
            throw failure;
        }
    }

    /** A body that cannot be read is treated as empty; it never changes the classification. */
    private static byte[] readBody(ClientHttpResponse response) {
        try (InputStream body = response.getBody()) {
            return body.readNBytes(TolerantErrorBodyParser.MAX_BODY_BYTES);
        }
        catch (IOException | RuntimeException ex) {
            return new byte[0];
        }
    }

    private static @Nullable MediaType contentType(ClientHttpResponse response) {
        try {
            return response.getHeaders().getContentType();
        }
        catch (RuntimeException ex) {
            return null;
        }
    }
}

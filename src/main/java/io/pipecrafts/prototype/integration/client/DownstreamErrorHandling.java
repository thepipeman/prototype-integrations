package io.pipecrafts.prototype.integration.client;

import java.util.function.Consumer;

import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import io.pipecrafts.prototype.integration.failure.DefaultFailureClassifier;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureLog;
import io.pipecrafts.prototype.integration.failure.DownstreamStatusHandler;
import io.pipecrafts.prototype.integration.failure.ErrorBodyParser;
import io.pipecrafts.prototype.integration.failure.FailureClassifier;

/**
 * Optional error translation for a {@link RestClient}: callers see {@code DownstreamBusinessException} and
 * {@code DependencyFailedException} instead of Spring's status and I/O exceptions.
 *
 * <p>Apply it before {@link RestClientCircuitBreakers} so its interceptor sits outside the breaker and can translate
 * an open circuit. Pass the same classifier instance to both so they never disagree about an outcome.
 */
public final class DownstreamErrorHandling {

    private final ErrorBodyParser errorBodyParser;
    private final DownstreamFailureLog failureLog;

    public DownstreamErrorHandling(ErrorBodyParser errorBodyParser, DownstreamFailureLog failureLog) {
        this.errorBodyParser = errorBodyParser;
        this.failureLog = failureLog;
    }

    public Consumer<RestClient.Builder> forClient(String name) {
        return forClient(name, DefaultFailureClassifier.standard());
    }

    public Consumer<RestClient.Builder> forClient(String name, FailureClassifier classifier) {
        if (!StringUtils.hasText(name)) {
            throw new IllegalArgumentException("Downstream name must not be blank");
        }
        return builder -> builder
                // Matches every status so Spring's default 4xx/5xx handler is never reached.
                .defaultStatusHandler(status -> true,
                        new DownstreamStatusHandler(name, classifier, errorBodyParser, failureLog))
                .requestInterceptor(new DownstreamFailureInterceptor(name, failureLog));
    }
}

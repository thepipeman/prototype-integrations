package io.pipecrafts.prototype.integration.client;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.pipecrafts.prototype.integration.failure.CircuitOpenIOException;
import io.pipecrafts.prototype.integration.failure.FailureClassifier;

/**
 * Innermost interceptor: records each call's outcome on the client's breaker from raw statuses and raw
 * {@link IOException}s, using the same classifier as the status handler.
 *
 * <p>Every acquired permission is recorded or released exactly once, including on {@link Error}s; a leaked
 * permission would leave a HALF_OPEN breaker stuck. The breaker is looked up per call so a registry
 * {@code reset()} can never leave this holding a stale instance.
 */
final class CircuitBreakerInterceptor implements ClientHttpRequestInterceptor {

    private final String name;
    private final CircuitBreakerRegistry registry;
    private final FailureClassifier classifier;

    CircuitBreakerInterceptor(String name, CircuitBreakerRegistry registry, FailureClassifier classifier) {
        this.name = name;
        this.registry = registry;
        this.classifier = classifier;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution)
            throws IOException {
        CircuitBreaker breaker = registry.circuitBreaker(name);
        if (!breaker.tryAcquirePermission()) {
            throw new CircuitOpenIOException(name);
        }
        TimeUnit unit = breaker.getTimestampUnit();
        long start = breaker.getCurrentTimestamp();
        boolean recorded = false;
        try {
            ClientHttpResponse response;
            try {
                response = BufferedClientHttpResponse.drain(execution.execute(request, body));
            }
            catch (IOException | RuntimeException ex) {
                breaker.onError(breaker.getCurrentTimestamp() - start, unit, ex);
                recorded = true;
                throw ex;
            }
            long elapsed = breaker.getCurrentTimestamp() - start;
            HttpStatusCode status = response.getStatusCode();
            switch (classifier.classify(status).breakerOutcome()) {
                case SUCCESS -> breaker.onSuccess(elapsed, unit);
                case FAILURE -> breaker.onError(elapsed, unit, new HttpStatusFailure(name, status));
                case IGNORED -> breaker.releasePermission();
            }
            recorded = true;
            return response;
        }
        finally {
            if (!recorded) {
                breaker.releasePermission();
            }
        }
    }

    /** Stackless carrier so Resilience4j events and metrics have a throwable for a failed status. */
    static final class HttpStatusFailure extends RuntimeException {

        HttpStatusFailure(String name, HttpStatusCode status) {
            super(name + " responded " + status.value(), null, false, false);
        }
    }
}

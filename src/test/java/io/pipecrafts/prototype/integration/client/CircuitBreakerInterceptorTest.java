package io.pipecrafts.prototype.integration.client;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig.SlidingWindowType;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.pipecrafts.prototype.integration.failure.CircuitOpenIOException;
import io.pipecrafts.prototype.integration.failure.DefaultFailureClassifier;

class CircuitBreakerInterceptorTest {

    private static final String NAME = "account-service";

    private final AtomicLong nanos = new AtomicLong();
    private final AtomicInteger executions = new AtomicInteger();
    private final MockClientHttpRequest request = new MockClientHttpRequest(HttpMethod.GET, URI.create("/x"));

    private CircuitBreaker breaker;
    private CircuitBreakerInterceptor interceptor;

    @BeforeEach
    void setUp() {
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .slidingWindow(10, 10, SlidingWindowType.COUNT_BASED)
                .slowCallDurationThreshold(Duration.ofMillis(300))
                .permittedNumberOfCallsInHalfOpenState(1)
                .currentTimestampFunction(clock -> nanos.get(), TimeUnit.NANOSECONDS)
                .build();
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(config);
        breaker = registry.circuitBreaker(NAME);
        interceptor = new CircuitBreakerInterceptor(NAME, registry, DefaultFailureClassifier.standard());
    }

    private ClientHttpRequestExecution responding(HttpStatus status, String body) {
        return (req, bytes) -> {
            executions.incrementAndGet();
            return new MockClientHttpResponse(body.getBytes(UTF_8), status);
        };
    }

    private void halfOpen() {
        breaker.transitionToOpenState();
        breaker.transitionToHalfOpenState();
    }

    @Test
    void successIsRecordedAndBodyIsBuffered() throws IOException {
        ClientHttpResponse response = interceptor.intercept(request, new byte[0], responding(HttpStatus.OK, "{\"a\":1}"));

        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(response.getBody().readAllBytes()).asString(UTF_8).isEqualTo("{\"a\":1}");
        assertThat(response.getBody().readAllBytes()).as("re-readable").asString(UTF_8).isEqualTo("{\"a\":1}");
    }

    @Test
    void businessErrorCountsAsSuccess() throws IOException {
        interceptor.intercept(request, new byte[0], responding(HttpStatus.NOT_FOUND, ""));

        assertThat(breaker.getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(1);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Test
    void serverErrorIsRecordedAsFailureAndResponseStillReturned() throws IOException {
        ClientHttpResponse response = interceptor.intercept(request, new byte[0],
                responding(HttpStatus.SERVICE_UNAVAILABLE, "down"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void ioExceptionIsRecordedAsFailureAndRethrownUnchanged() {
        SocketTimeoutException timeout = new SocketTimeoutException("Read timed out");

        assertThatThrownBy(() -> interceptor.intercept(request, new byte[0], (req, bytes) -> {
            throw timeout;
        })).isSameAs(timeout);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void bodyReadFailureIsRecordedInsideTheBreaker() {
        ClientHttpRequestExecution brokenBody = (req, bytes) -> new MockClientHttpResponse(new InputStream() {
            @Override
            public int read() throws IOException {
                throw new SocketTimeoutException("Read timed out mid-body");
            }
        }, HttpStatus.OK);

        assertThatExceptionOfType(SocketTimeoutException.class)
                .isThrownBy(() -> interceptor.intercept(request, new byte[0], brokenBody));
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
    }

    @Test
    void openBreakerFailsFastWithoutContactingTheDownstream() {
        breaker.transitionToOpenState();

        assertThatExceptionOfType(CircuitOpenIOException.class)
                .isThrownBy(() -> interceptor.intercept(request, new byte[0], responding(HttpStatus.OK, "")));
        assertThat(executions).hasValue(0);
        assertThat(breaker.getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);
    }

    @Test
    void slowCallIsMeasuredWithTheBreakerClockIncludingBodyTransfer() throws IOException {
        ClientHttpRequestExecution slow = (req, bytes) -> new MockClientHttpResponse(new InputStream() {
            private boolean done;

            @Override
            public int read() {
                if (done) {
                    return -1;
                }
                nanos.addAndGet(Duration.ofMillis(400).toNanos());
                done = true;
                return 'x';
            }
        }, HttpStatus.OK);

        interceptor.intercept(request, new byte[0], slow);

        assertThat(breaker.getMetrics().getNumberOfSlowSuccessfulCalls()).isEqualTo(1);
    }

    @Test
    void ignoredStatusReleasesTheHalfOpenPermission() throws IOException {
        halfOpen();

        interceptor.intercept(request, new byte[0], responding(HttpStatus.UNAUTHORIZED, ""));

        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
        assertThat(breaker.getState()).isEqualTo(State.HALF_OPEN);
        assertThat(breaker.tryAcquirePermission()).as("next probe permitted").isTrue();
    }

    @Test
    void errorReleasesTheHalfOpenPermission() {
        halfOpen();

        assertThatExceptionOfType(StackOverflowError.class).isThrownBy(
                () -> interceptor.intercept(request, new byte[0], (req, bytes) -> {
                    throw new StackOverflowError();
                }));

        assertThat(breaker.getState()).isEqualTo(State.HALF_OPEN);
        assertThat(breaker.tryAcquirePermission()).as("next probe permitted").isTrue();
    }

    @Test
    void halfOpenProbeOutcomeIsRecordedExactlyOnce() throws IOException {
        halfOpen();

        interceptor.intercept(request, new byte[0], responding(HttpStatus.OK, ""));

        assertThat(breaker.getState()).isEqualTo(State.CLOSED);
    }

    @Test
    void failedHalfOpenProbeReopens() {
        halfOpen();

        assertThatExceptionOfType(IOException.class).isThrownBy(
                () -> interceptor.intercept(request, new byte[0], (req, bytes) -> {
                    throw new IOException("Connection reset");
                }));

        assertThat(breaker.getState()).isEqualTo(State.OPEN);
    }
}

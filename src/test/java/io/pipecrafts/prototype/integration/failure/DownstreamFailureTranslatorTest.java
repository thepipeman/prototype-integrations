package io.pipecrafts.prototype.integration.failure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

class DownstreamFailureTranslatorTest {

    static Stream<Arguments> ioFailures() {
        return Stream.of(
                Arguments.of(new CircuitOpenIOException("account-service"), FailureReason.CIRCUIT_OPEN),
                Arguments.of(new SocketTimeoutException("Read timed out"), FailureReason.TIMEOUT),
                Arguments.of(new ConnectTimeoutException("connect timed out"), FailureReason.TIMEOUT),
                Arguments.of(new ConnectException("Connection refused"), FailureReason.CONNECTION_FAILED),
                Arguments.of(new NoHttpResponseException("no response"), FailureReason.CONNECTION_FAILED),
                Arguments.of(new UnknownHostException("nowhere"), FailureReason.CONNECTION_FAILED),
                Arguments.of(new IOException("wrapped", new SocketTimeoutException("inner")), FailureReason.TIMEOUT));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("ioFailures")
    void resourceAccessFailuresAreClassifiedByTheirIoCause(IOException cause, FailureReason expected) {
        ResourceAccessException failure = new ResourceAccessException("I/O error on GET http://x?mobile=0917", cause);

        RuntimeException translated = DownstreamFailureTranslator.translate(failure, "account-service", "A#b");

        assertThat(translated).isInstanceOfSatisfying(DependencyFailedException.class, dependency -> {
            assertThat(dependency.reason()).isEqualTo(expected);
            assertThat(dependency.category()).isEqualTo(FailureCategory.DEPENDENCY_FAILURE);
            assertThat(dependency.dependency()).isEqualTo("account-service");
            assertThat(dependency.operation()).isEqualTo("A#b");
            assertThat(dependency.httpStatus()).isEmpty();
            assertThat(dependency.getCause()).isSameAs(failure);
            assertThat(dependency.getMessage()).doesNotContain("http://", "mobile");
        });
    }

    @Test
    void undecodableBodyIsMalformedResponse() {
        RestClientException failure = new RestClientException("Error while extracting response",
                new HttpMessageNotReadableException("bad json", new MockClientHttpResponse(new byte[0], 200)));

        assertThat(DownstreamFailureTranslator.translate(failure, "d", "o"))
                .isInstanceOfSatisfying(DependencyFailedException.class,
                        dependency -> assertThat(dependency.reason()).isEqualTo(FailureReason.MALFORMED_RESPONSE));
    }

    @Test
    void ioErrorWhileDecodingUnbufferedBodyUsesTheIoRule() {
        RestClientException failure = new RestClientException("Error while extracting response",
                new SocketTimeoutException("Read timed out"));

        assertThat(DownstreamFailureTranslator.translate(failure, "d", "o"))
                .isInstanceOfSatisfying(DependencyFailedException.class,
                        dependency -> assertThat(dependency.reason()).isEqualTo(FailureReason.TIMEOUT));
    }

    @Test
    void downstreamExceptionsPassThroughAsTheSameInstance() {
        DownstreamBusinessException business = new DownstreamBusinessException("d", "o", HttpStatusCode.valueOf(404),
                DownstreamError.empty(), null);

        assertThat(DownstreamFailureTranslator.translate(business, "d", "o")).isSameAs(business);
    }

    @Test
    void unrelatedExceptionsPassThroughUntouched() {
        IllegalStateException bug = new IllegalStateException("bug");

        assertThat(DownstreamFailureTranslator.translate(bug, "d", "o")).isSameAs(bug);
    }
}

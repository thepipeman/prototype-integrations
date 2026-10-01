package io.pipecrafts.prototype.integration.failure;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.stream.Stream;

import org.apache.hc.client5.http.ConnectTimeoutException;
import org.apache.hc.core5.http.NoHttpResponseException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
    void ioFailuresAreClassifiedByTheirCauseChain(IOException failure, FailureReason expected) {
        assertThat(DownstreamFailureTranslator.ioReason(failure)).isEqualTo(expected);
    }
}

package io.pipecrafts.prototype.integration.client;

import java.io.IOException;

import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import io.pipecrafts.prototype.integration.failure.DependencyFailedException;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureLog;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureTranslator;
import io.pipecrafts.prototype.integration.failure.DownstreamOperation;

/**
 * Turns transport failures (timeouts, refused connections, open circuit) into a logged
 * {@link DependencyFailedException}. RestClient rethrows runtime exceptions from interceptors unchanged, so callers
 * see this instead of a {@code ResourceAccessException}.
 */
final class DownstreamFailureInterceptor implements ClientHttpRequestInterceptor {

    private final String dependency;
    private final DownstreamFailureLog failureLog;

    DownstreamFailureInterceptor(String dependency, DownstreamFailureLog failureLog) {
        this.dependency = dependency;
        this.failureLog = failureLog;
    }

    @Override
    public ClientHttpResponse intercept(HttpRequest request, byte[] body, ClientHttpRequestExecution execution) {
        try {
            return execution.execute(request, body);
        }
        catch (IOException ex) {
            DependencyFailedException failure = DependencyFailedException.ofTransport(dependency,
                    DownstreamOperation.of(request), DownstreamFailureTranslator.ioReason(ex), ex);
            failureLog.record(failure);
            throw failure;
        }
    }
}

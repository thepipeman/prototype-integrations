package io.pipecrafts.prototype.integration.client;

import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.service.invoker.HttpExchangeAdapter;
import org.springframework.web.service.invoker.HttpExchangeAdapterDecorator;
import org.springframework.web.service.invoker.HttpRequestValues;

import io.pipecrafts.prototype.integration.failure.DownstreamException;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureLog;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureTranslator;
import io.pipecrafts.prototype.integration.failure.DownstreamOperation;

/**
 * Outermost layer of every client call: translates client failures into {@link DownstreamException}s and logs
 * each one exactly once.
 */
final class DownstreamExchangeAdapter extends HttpExchangeAdapterDecorator {

    private final String dependency;
    private final DownstreamFailureLog failureLog;

    DownstreamExchangeAdapter(HttpExchangeAdapter delegate, String dependency, DownstreamFailureLog failureLog) {
        super(delegate);
        this.dependency = dependency;
        this.failureLog = failureLog;
    }

    @Override
    public void exchange(HttpRequestValues values) {
        translate(values, () -> {
            super.exchange(values);
            return null;
        });
    }

    @Override
    public HttpHeaders exchangeForHeaders(HttpRequestValues values) {
        return translate(values, () -> super.exchangeForHeaders(values));
    }

    @Override
    public <T> @Nullable T exchangeForBody(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
        return translate(values, () -> super.exchangeForBody(values, bodyType));
    }

    @Override
    public ResponseEntity<Void> exchangeForBodilessEntity(HttpRequestValues values) {
        return translate(values, () -> super.exchangeForBodilessEntity(values));
    }

    @Override
    public <T> ResponseEntity<T> exchangeForEntity(HttpRequestValues values, ParameterizedTypeReference<T> bodyType) {
        return translate(values, () -> super.exchangeForEntity(values, bodyType));
    }

    private <T extends @Nullable Object> T translate(HttpRequestValues values, Supplier<T> call) {
        try {
            return call.get();
        }
        catch (RuntimeException ex) {
            String operation = DownstreamOperation.from(values.getAttributes());
            RuntimeException translated = DownstreamFailureTranslator.translate(ex, dependency, operation);
            if (translated instanceof DownstreamException failure) {
                failureLog.record(failure);
            }
            throw translated;
        }
    }
}

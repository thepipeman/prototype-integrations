package io.pipecrafts.prototype.integration.client;

import java.util.function.Consumer;

import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.HttpClientProperties;
import org.springframework.boot.http.client.autoconfigure.HttpClientSettingsPropertyMapper;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientProperties;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.pipecrafts.prototype.integration.failure.DefaultFailureClassifier;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureLog;
import io.pipecrafts.prototype.integration.failure.DownstreamStatusHandler;
import io.pipecrafts.prototype.integration.failure.ErrorBodyParser;
import io.pipecrafts.prototype.integration.failure.FailureClassifier;

/**
 * Builds the {@link RestClient} for one downstream with the shared error handling and circuit breaker applied.
 *
 * <p>The name is the key everywhere: transport comes from Boot's {@code spring.http.serviceclient.<name>} (base URL,
 * timeouts, default headers, SSL bundle), breaker tuning from {@code resilience4j.circuitbreaker.instances.<name>},
 * and the breaker switch from {@code prototype.clients.<name>}.
 *
 * <p>Interceptor order, outermost first: Boot's {@code RestClientCustomizer}s, failure translation, the per-client
 * customizer's, and the circuit breaker last so it sees raw statuses and raw I/O errors.
 */
public final class ResilientRestClients {

    private final RestClient.Builder baseBuilder;
    private final ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder;
    private final HttpClientSettingsPropertyMapper settingsMapper;
    private final HttpServiceClientProperties transportProperties;
    private final ResilientClientsProperties resilientProperties;
    private final CircuitBreakerRegistry breakerRegistry;
    private final ErrorBodyParser errorBodyParser;
    private final DownstreamFailureLog failureLog;

    ResilientRestClients(RestClient.Builder baseBuilder, ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder,
            HttpClientSettingsPropertyMapper settingsMapper, HttpServiceClientProperties transportProperties,
            ResilientClientsProperties resilientProperties, CircuitBreakerRegistry breakerRegistry,
            ErrorBodyParser errorBodyParser, DownstreamFailureLog failureLog) {
        this.baseBuilder = baseBuilder;
        this.requestFactoryBuilder = requestFactoryBuilder;
        this.settingsMapper = settingsMapper;
        this.transportProperties = transportProperties;
        this.resilientProperties = resilientProperties;
        this.breakerRegistry = breakerRegistry;
        this.errorBodyParser = errorBodyParser;
        this.failureLog = failureLog;
    }

    public RestClient create(String name) {
        return create(name, DefaultFailureClassifier.standard(), builder -> {
        });
    }

    public RestClient create(String name, Consumer<RestClient.Builder> customizer) {
        return create(name, DefaultFailureClassifier.standard(), customizer);
    }

    /**
     * @param classifier replaces the default taxonomy for this client, for the breaker and the status handler alike
     * @param customizer runs after the defaults, for extra headers, interceptors or converters; it cannot change
     *        classification because the shared status handler matches every status first
     */
    public RestClient create(String name, FailureClassifier classifier, Consumer<RestClient.Builder> customizer) {
        HttpClientProperties transport = transportProperties.get(name);
        if (transport == null || !StringUtils.hasText(transport.getBaseUrl())) {
            throw new IllegalStateException(
                    "Downstream '%s' has no spring.http.serviceclient.%s.base-url".formatted(name, name));
        }

        RestClient.Builder builder = baseBuilder.clone()
                .baseUrl(transport.getBaseUrl())
                .defaultHeaders(headers -> transport.getDefaultHeader().forEach(headers::addAll))
                .requestFactory(requestFactoryBuilder.build(settingsMapper.map(transport)))
                // Matches every status so Spring's default 4xx/5xx handler is never reached.
                .defaultStatusHandler(status -> true,
                        new DownstreamStatusHandler(name, classifier, errorBodyParser, failureLog))
                .requestInterceptor(new DownstreamFailureInterceptor(name, failureLog));

        customizer.accept(builder);

        if (resilientProperties.breakerEnabled(name)) {
            builder.requestInterceptor(new CircuitBreakerInterceptor(name, breakerRegistry, classifier));
            // Created eagerly so actuator shows every client from startup, not only after its first call.
            breakerRegistry.circuitBreaker(name);
        }
        return builder.build();
    }
}

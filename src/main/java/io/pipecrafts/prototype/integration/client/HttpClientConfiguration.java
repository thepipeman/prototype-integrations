package io.pipecrafts.prototype.integration.client;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.boot.http.client.autoconfigure.HttpClientSettingsPropertyMapper;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientProperties;
import org.springframework.boot.ssl.SslBundles;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureLog;
import io.pipecrafts.prototype.integration.failure.ErrorBodyParser;
import io.pipecrafts.prototype.integration.failure.TolerantErrorBodyParser;
import tools.jackson.databind.json.JsonMapper;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ResilientClientsProperties.class)
public class HttpClientConfiguration {

    /**
     * HttpClient5 otherwise resends on 429/503 after a second regardless of method (including POSTs) and on some
     * I/O errors, hiding attempts inside one breaker measurement.
     */
    @Bean
    ClientHttpRequestFactoryBuilderCustomizer<HttpComponentsClientHttpRequestFactoryBuilder> disableHttpClientRetries() {
        return builder -> builder.withHttpClientCustomizer(HttpClientBuilder::disableAutomaticRetries);
    }

    @Bean
    DownstreamFailureLog downstreamFailureLog(MeterRegistry meterRegistry) {
        return new DownstreamFailureLog(meterRegistry);
    }

    /**
     * Starts from Boot's {@code RestClient.Builder}, so global {@code RestClientCustomizer}s, message converters
     * and observations apply to every client. Defining an {@link ErrorBodyParser} bean replaces the tolerant
     * default for every client.
     */
    @Bean
    ResilientRestClients resilientRestClients(RestClient.Builder restClientBuilder,
            ClientHttpRequestFactoryBuilder<?> requestFactoryBuilder, ObjectProvider<SslBundles> sslBundles,
            ObjectProvider<HttpClientSettings> globalSettings, HttpServiceClientProperties transportProperties,
            ResilientClientsProperties resilientProperties, CircuitBreakerRegistry breakerRegistry,
            ObjectProvider<ErrorBodyParser> errorBodyParser, JsonMapper jsonMapper, DownstreamFailureLog failureLog) {
        return new ResilientRestClients(restClientBuilder, requestFactoryBuilder,
                new HttpClientSettingsPropertyMapper(sslBundles.getIfAvailable(), globalSettings.getIfAvailable()),
                transportProperties, resilientProperties, breakerRegistry,
                errorBodyParser.getIfAvailable(() -> new TolerantErrorBodyParser(jsonMapper)), failureLog);
    }
}

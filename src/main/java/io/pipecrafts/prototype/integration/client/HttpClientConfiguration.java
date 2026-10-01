package io.pipecrafts.prototype.integration.client;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.HttpComponentsClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.autoconfigure.ClientHttpRequestFactoryBuilderCustomizer;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

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

    /** Defining an {@link ErrorBodyParser} bean replaces the tolerant default for every client. */
    @Bean
    ResilientHttpServiceGroupConfigurer resilientHttpServiceGroupConfigurer(ListableBeanFactory beanFactory,
            CircuitBreakerRegistry breakerRegistry, ResilientClientsProperties resilientProperties,
            HttpServiceClientProperties serviceClientProperties, ObjectProvider<ErrorBodyParser> errorBodyParser,
            JsonMapper jsonMapper, DownstreamFailureLog failureLog) {
        return new ResilientHttpServiceGroupConfigurer(beanFactory, breakerRegistry, resilientProperties,
                serviceClientProperties, errorBodyParser.getIfAvailable(() -> new TolerantErrorBodyParser(jsonMapper)),
                failureLog);
    }
}

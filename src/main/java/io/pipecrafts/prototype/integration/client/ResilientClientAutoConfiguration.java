package io.pipecrafts.prototype.integration.client;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.client.RestClient;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureLog;
import io.pipecrafts.prototype.integration.failure.ErrorBodyParser;
import io.pipecrafts.prototype.integration.failure.TolerantErrorBodyParser;
import tools.jackson.databind.json.JsonMapper;

/** Provides the beans projects use to put a circuit breaker (and optionally error translation) on their clients. */
@AutoConfiguration(after = CircuitBreakerAutoConfiguration.class)
@ConditionalOnClass({ RestClient.class, CircuitBreakerRegistry.class })
@ConditionalOnBean(CircuitBreakerRegistry.class)
@EnableConfigurationProperties(ResilientClientProperties.class)
public class ResilientClientAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    RestClientCircuitBreakers restClientCircuitBreakers(CircuitBreakerRegistry registry,
            ResilientClientProperties properties) {
        return new RestClientCircuitBreakers(registry, properties);
    }

    @Bean
    @ConditionalOnMissingBean
    DownstreamFailureLog downstreamFailureLog(ObjectProvider<MeterRegistry> meterRegistry) {
        return new DownstreamFailureLog(meterRegistry.getIfAvailable());
    }

    @Bean
    @ConditionalOnMissingBean
    DownstreamErrorHandling downstreamErrorHandling(ObjectProvider<ErrorBodyParser> errorBodyParser,
            ObjectProvider<JsonMapper> jsonMapper, DownstreamFailureLog failureLog) {
        return new DownstreamErrorHandling(
                errorBodyParser.getIfAvailable(
                        () -> new TolerantErrorBodyParser(jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()))),
                failureLog);
    }
}

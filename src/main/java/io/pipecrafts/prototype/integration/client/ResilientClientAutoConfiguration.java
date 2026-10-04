package io.pipecrafts.prototype.integration.client;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.micrometer.core.instrument.MeterRegistry;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureLog;
import io.pipecrafts.prototype.integration.failure.ErrorBodyParser;
import io.pipecrafts.prototype.integration.failure.TolerantErrorBodyParser;
import tools.jackson.databind.json.JsonMapper;

/**
 * Instruments the {@code RestClient}s listed under {@code resilient-client.clients}. Every bean is replaceable
 * ({@code @ConditionalOnMissingBean}), and {@code resilient-client.enabled=false} switches off the instrumentation
 * and the validation while keeping {@link ResilientClients} injectable.
 */
@AutoConfiguration(afterName = {
        "io.github.resilience4j.springboot.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration",
        "org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration",
        "org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration" })
@ConditionalOnClass({ RestClient.class, CircuitBreakerRegistry.class })
@EnableConfigurationProperties(ResilientClientProperties.class)
public class ResilientClientAutoConfiguration {

    private static final String ENABLED = "resilient-client.enabled";

    @Bean
    @ConditionalOnMissingBean
    DownstreamFailureLog downstreamFailureLog(MeterRegistry meterRegistry) {
        return new DownstreamFailureLog(meterRegistry);
    }

    /** Defining an {@link ErrorBodyParser} bean replaces the tolerant default for every client. */
    @Bean
    @ConditionalOnMissingBean
    ErrorBodyParser errorBodyParser(ObjectProvider<JsonMapper> jsonMapper) {
        return new TolerantErrorBodyParser(jsonMapper.getIfAvailable(() -> JsonMapper.builder().build()));
    }

    @Bean
    @ConditionalOnMissingBean
    ClientPolicyResolver clientPolicyResolver(ResilientClientProperties properties, ListableBeanFactory beanFactory) {
        return new ClientPolicyResolver(properties, beanFactory);
    }

    @Bean
    @ConditionalOnMissingBean
    ResilientClients resilientClients(ResilientClientProperties properties, ClientPolicyResolver policies,
            CircuitBreakerRegistry breakerRegistry, ErrorBodyParser errorBodyParser, DownstreamFailureLog failureLog,
            ObjectProvider<ResilientClientCustomizer> customizers) {
        return new ResilientClients(properties, policies, breakerRegistry, errorBodyParser, failureLog, customizers);
    }

    @Bean
    @ConditionalOnProperty(name = ENABLED, matchIfMissing = true)
    static ResilientClientInstrumenter resilientClientInstrumenter(ObjectProvider<ResilientClientProperties> properties,
            ObjectProvider<ResilientClients> resilientClients) {
        return new ResilientClientInstrumenter(properties, resilientClients);
    }

    @Bean
    @ConditionalOnProperty(name = ENABLED, matchIfMissing = true)
    ResilientClientsValidator resilientClientsValidator(ResilientClientProperties properties,
            ListableBeanFactory beanFactory, ClientPolicyResolver policies,
            ObjectProvider<ResilientClientGroupConfigurer> groupConfigurer) {
        return new ResilientClientsValidator(properties, beanFactory, policies, groupConfigurer);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RestClientHttpServiceGroupConfigurer.class)
    @ConditionalOnProperty(name = ENABLED, matchIfMissing = true)
    static class HttpServiceGroupConfiguration {

        @Bean
        ResilientClientGroupConfigurer resilientClientGroupConfigurer(ResilientClientProperties properties,
                ResilientClients resilientClients) {
            return new ResilientClientGroupConfigurer(properties, resilientClients);
        }
    }
}

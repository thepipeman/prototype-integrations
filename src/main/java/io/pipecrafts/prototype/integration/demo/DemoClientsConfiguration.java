package io.pipecrafts.prototype.integration.demo;

import static io.pipecrafts.prototype.integration.demo.DemoClients.ACCOUNT;
import static io.pipecrafts.prototype.integration.demo.DemoClients.NOTIFICATION;
import static io.pipecrafts.prototype.integration.demo.DemoClients.PROFILE;
import static io.pipecrafts.prototype.integration.demo.DemoClients.VERIFICATION;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import io.pipecrafts.prototype.integration.demo.DemoClientsProperties.Endpoint;
import io.pipecrafts.prototype.integration.demo.profile.generated.ApiClient;
import io.pipecrafts.prototype.integration.demo.profile.generated.api.ProfileApi;

/**
 * Plain {@code RestClient} beans, one per downstream, as an existing project would define them. Nothing here
 * references the resilience library: the {@code resilient-client} YAML adds the circuit breaker and error handling.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoClientsProperties.class)
class DemoClientsConfiguration {

    /** The generated client builds its own {@code RestClient}; exposing it as a bean lets it be instrumented. */
    @Bean
    RestClient profileRestClient(DemoClientsProperties properties) {
        return ApiClient.buildRestClientBuilder().requestFactory(requestFactory(properties.get(PROFILE))).build();
    }

    @Bean
    ProfileApi profileApi(@Qualifier("profileRestClient") RestClient profileRestClient,
            DemoClientsProperties properties) {
        ApiClient apiClient = new ApiClient(profileRestClient);
        apiClient.setBasePath(properties.get(PROFILE).baseUrl());
        return new ProfileApi(apiClient);
    }

    @Bean
    RestClient verificationRestClient(RestClient.Builder builder, DemoClientsProperties properties) {
        return restClient(builder, properties.get(VERIFICATION));
    }

    @Bean
    RestClient accountRestClient(RestClient.Builder builder, DemoClientsProperties properties) {
        return restClient(builder, properties.get(ACCOUNT));
    }

    @Bean
    RestClient notificationRestClient(RestClient.Builder builder, DemoClientsProperties properties) {
        return restClient(builder, properties.get(NOTIFICATION));
    }

    private static RestClient restClient(RestClient.Builder builder, Endpoint endpoint) {
        return builder.baseUrl(endpoint.baseUrl())
                .defaultHeaders(headers -> endpoint.defaultHeader().forEach(headers::add))
                .requestFactory(requestFactory(endpoint))
                .build();
    }

    /**
     * HttpClient5 otherwise resends on 429/503 after a second regardless of method (including POSTs) and on some
     * I/O errors, hiding attempts inside one breaker measurement.
     */
    private static ClientHttpRequestFactory requestFactory(Endpoint endpoint) {
        return ClientHttpRequestFactoryBuilder.httpComponents()
                .withHttpClientCustomizer(HttpClientBuilder::disableAutomaticRetries)
                .build(HttpClientSettings.defaults().withTimeouts(endpoint.connectTimeout(), endpoint.readTimeout()));
    }
}

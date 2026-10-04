package io.pipecrafts.prototype.integration.demo;

import static io.pipecrafts.prototype.integration.demo.DemoClients.ACCOUNT;
import static io.pipecrafts.prototype.integration.demo.DemoClients.NOTIFICATION;
import static io.pipecrafts.prototype.integration.demo.DemoClients.PROFILE;
import static io.pipecrafts.prototype.integration.demo.DemoClients.VERIFICATION;

import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.HttpClientSettings;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import io.pipecrafts.prototype.integration.client.DownstreamErrorHandling;
import io.pipecrafts.prototype.integration.client.RestClientCircuitBreakers;
import io.pipecrafts.prototype.integration.demo.DemoClientsProperties.Client;
import io.pipecrafts.prototype.integration.demo.profile.generated.api.ProfileApi;
import io.pipecrafts.prototype.integration.demo.profile.generated.invoker.ApiClient;
import io.pipecrafts.prototype.integration.failure.Classification;
import io.pipecrafts.prototype.integration.failure.DefaultFailureClassifier;
import io.pipecrafts.prototype.integration.failure.FailureClassifier;

/**
 * Stands in for the client configuration an existing project already has: one {@code RestClient} per downstream,
 * each gaining the circuit breaker (and here also error translation) with one {@code apply(...)} line.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(DemoClientsProperties.class)
public class DemoClientsConfiguration {

    /** OpenAPI-generated client: start from the generated builder so its JSON converter and date handling survive. */
    @Bean
    ProfileApi profileApi(DemoClientsProperties properties, DownstreamErrorHandling errorHandling,
            RestClientCircuitBreakers circuitBreakers) {
        Client client = properties.client(PROFILE);
        RestClient restClient = ApiClient.buildRestClientBuilder()
                .requestFactory(requestFactory(client))
                .apply(errorHandling.forClient(PROFILE))
                .apply(circuitBreakers.forClient(PROFILE))
                .build();
        ApiClient apiClient = new ApiClient(restClient);
        apiClient.setBasePath(client.baseUrl());
        return new ProfileApi(apiClient);
    }

    @Bean(VERIFICATION)
    RestClient verificationRestClient(RestClient.Builder builder, DemoClientsProperties properties,
            DownstreamErrorHandling errorHandling, RestClientCircuitBreakers circuitBreakers) {
        return base(builder, properties.client(VERIFICATION))
                .apply(errorHandling.forClient(VERIFICATION))
                .apply(circuitBreakers.forClient(VERIFICATION))
                .build();
    }

    @Bean(ACCOUNT)
    RestClient accountRestClient(RestClient.Builder builder, DemoClientsProperties properties,
            DownstreamErrorHandling errorHandling, RestClientCircuitBreakers circuitBreakers) {
        return base(builder, properties.client(ACCOUNT))
                .apply(errorHandling.forClient(ACCOUNT))
                .apply(circuitBreakers.forClient(ACCOUNT))
                .build();
    }

    /** Own failure policy and an API key header, without touching any other client. */
    @Bean(NOTIFICATION)
    RestClient notificationRestClient(RestClient.Builder builder, DemoClientsProperties properties,
            DownstreamErrorHandling errorHandling, RestClientCircuitBreakers circuitBreakers,
            @Value("${demo.notification.api-key}") String apiKey) {
        // A 409 means "duplicate submission, already accepted" and carries a normal receipt. Rejected credentials
        // count as breaker failures: a non-critical dependency with broken auth should stop being called.
        FailureClassifier policy = DefaultFailureClassifier.builder()
                .countRejectedAsFailure(true)
                .override(409, Classification.success())
                .build();
        return base(builder, properties.client(NOTIFICATION))
                .defaultHeader("X-Api-Key", apiKey)
                .apply(errorHandling.forClient(NOTIFICATION, policy))
                .apply(circuitBreakers.forClient(NOTIFICATION, policy))
                .build();
    }

    private static RestClient.Builder base(RestClient.Builder builder, Client client) {
        return builder.clone()
                .baseUrl(client.baseUrl())
                .defaultHeaders(headers -> client.defaultHeader().forEach(headers::add))
                .requestFactory(requestFactory(client));
    }

    /**
     * HttpClient5 otherwise resends on 429/503 after a second regardless of method (including POSTs) and on some I/O
     * errors, hiding several attempts inside one breaker measurement.
     */
    private static ClientHttpRequestFactory requestFactory(Client client) {
        return ClientHttpRequestFactoryBuilder.httpComponents()
                .withHttpClientCustomizer(HttpClientBuilder::disableAutomaticRetries)
                .build(HttpClientSettings.defaults()
                        .withConnectTimeout(client.connectTimeout())
                        .withReadTimeout(client.readTimeout()));
    }
}

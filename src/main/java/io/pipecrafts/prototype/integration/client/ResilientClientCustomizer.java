package io.pipecrafts.prototype.integration.client;

import org.springframework.web.client.RestClient;

/**
 * Adjusts a listed client while it is instrumented, for every client; implementations switch on {@code clientName}.
 *
 * <p>Runs after error handling and before the circuit breaker: an interceptor added here is measured by the breaker
 * but its exceptions are not translated.
 */
@FunctionalInterface
public interface ResilientClientCustomizer {

    void customize(String clientName, RestClient.Builder builder);
}

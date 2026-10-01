package io.pipecrafts.prototype.integration.client;

import org.springframework.web.client.RestClient;

/**
 * Per-client escape hatch: a bean {@code @Qualifier}-ed with a group name receives that group's builder after all
 * defaults are applied. Interceptors it adds always run outside the circuit breaker.
 *
 * <p>It cannot change classification (use a qualified {@code FailureClassifier} bean), and it must not depend on
 * HTTP service proxies: it runs while the proxy registry is being built.
 */
@FunctionalInterface
public interface ResilientClientCustomizer {

    void customize(RestClient.Builder builder);
}

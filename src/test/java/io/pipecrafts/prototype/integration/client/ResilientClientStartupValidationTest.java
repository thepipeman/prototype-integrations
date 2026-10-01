package io.pipecrafts.prototype.integration.client;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;

import io.pipecrafts.prototype.integration.PrototypeIntegrationsApplication;

class ResilientClientStartupValidationTest {

    private static void start(String... args) {
        try (var context = new SpringApplicationBuilder(PrototypeIntegrationsApplication.class)
                .web(WebApplicationType.NONE)
                .run(args)) {
            context.getId();
        }
    }

    @Test
    void misspelledResilienceKeyFailsStartup() {
        assertThatThrownBy(() -> start("--prototype.clients.acount-service.breaker.enabled=false"))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prototype.clients.acount-service does not match any registered");
    }

    @Test
    void missingBaseUrlFailsStartup() {
        assertThatThrownBy(() -> start("--spring.http.serviceclient.profile-service.base-url="))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("spring.http.serviceclient.profile-service.base-url");
    }
}

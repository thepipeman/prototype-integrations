package io.pipecrafts.prototype.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static io.pipecrafts.prototype.integration.demo.DemoClients.ACCOUNT;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.pipecrafts.prototype.integration.support.ResilientIntegrationTestBase;

/** Runs in its own application context (and its own WireMock servers) because the property changes wiring. */
@TestPropertySource(properties = "prototype.clients.account-service.breaker.enabled=false")
@DisplayName("Circuit breaker disabled by property")
class BreakerDisabledByPropertyIntegrationTest extends ResilientIntegrationTestBase {

    @Test
    @DisplayName("17. breaker.enabled=false: repeated 5xx never open it, every call reaches the downstream")
    void repeatedServerErrorsNeverOpenDisabledBreaker() {
        accountServer.stubFor(get(urlEqualTo("/accounts/A1/balance")).willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 8; i++) {
            assertProblem(apiGet("/demo/accounts/A1/balance"), 502, "DEPENDENCY_FAILED");
        }

        accountServer.verify(exactly(8), getRequestedFor(urlEqualTo("/accounts/A1/balance")));
        CircuitBreaker breaker = breaker(ACCOUNT);
        assertThat(breaker.getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfBufferedCalls()).isZero();
    }
}

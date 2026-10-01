package io.pipecrafts.prototype.integration.support;

import static io.pipecrafts.prototype.integration.demo.DemoClients.ACCOUNT;
import static io.pipecrafts.prototype.integration.demo.DemoClients.NOTIFICATION;
import static io.pipecrafts.prototype.integration.demo.DemoClients.PROFILE;
import static io.pipecrafts.prototype.integration.demo.DemoClients.VERIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;
import org.wiremock.spring.ConfigureWireMock;
import org.wiremock.spring.EnableWireMock;
import org.wiremock.spring.InjectWireMock;

import com.github.tomakehurst.wiremock.WireMockServer;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Boots the app against four WireMock downstreams. Breaker state is shared across the context, so these tests
 * must not run in parallel; breakers and stubs are reset after every test.
 */
@SpringBootTest(webEnvironment = RANDOM_PORT)
@ActiveProfiles("test")
@EnableWireMock({
        @ConfigureWireMock(name = PROFILE, baseUrlProperties = "spring.http.serviceclient.profile-service.base-url"),
        @ConfigureWireMock(name = VERIFICATION,
                baseUrlProperties = "spring.http.serviceclient.verification-service.base-url"),
        @ConfigureWireMock(name = ACCOUNT, baseUrlProperties = "spring.http.serviceclient.account-service.base-url"),
        @ConfigureWireMock(name = NOTIFICATION,
                baseUrlProperties = "spring.http.serviceclient.notification-service.base-url")
})
public abstract class ResilientIntegrationTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @InjectWireMock(PROFILE)
    protected WireMockServer profileServer;

    @InjectWireMock(VERIFICATION)
    protected WireMockServer verificationServer;

    @InjectWireMock(ACCOUNT)
    protected WireMockServer accountServer;

    @InjectWireMock(NOTIFICATION)
    protected WireMockServer notificationServer;

    @Autowired
    protected CircuitBreakerRegistry breakers;

    @LocalServerPort
    private int port;

    private RestClient api;

    @BeforeEach
    void createApiClient() {
        // Plain client outside the HTTP service registry: never resilient, never throws on status.
        api = RestClient.builder()
                .requestFactory(new JdkClientHttpRequestFactory())
                .baseUrl("http://localhost:" + port)
                .defaultStatusHandler(status -> true, (request, response) -> {
                })
                .build();
    }

    @AfterEach
    void resetBreakersAndStubs() {
        breakers.getAllCircuitBreakers().forEach(CircuitBreaker::reset);
        List.of(profileServer, verificationServer, accountServer, notificationServer)
                .forEach(WireMockServer::resetAll);
    }

    protected ResponseEntity<String> apiGet(String path) {
        return api.get().uri(path).retrieve().toEntity(String.class);
    }

    protected ResponseEntity<String> apiPost(String path, String json) {
        return api.post().uri(path).contentType(MediaType.APPLICATION_JSON).body(json).retrieve()
                .toEntity(String.class);
    }

    protected CircuitBreaker breaker(String name) {
        return breakers.circuitBreaker(name);
    }

    protected static JsonNode json(ResponseEntity<String> response) {
        assertThat(response.getBody()).as("response body").isNotNull();
        return JSON.readTree(response.getBody());
    }

    protected static void assertProblem(ResponseEntity<String> response, int status, String code) {
        assertThat(response.getStatusCode().value()).as("status of %s", response.getBody()).isEqualTo(status);
        assertThat(json(response).path("code").asString()).isEqualTo(code);
    }
}

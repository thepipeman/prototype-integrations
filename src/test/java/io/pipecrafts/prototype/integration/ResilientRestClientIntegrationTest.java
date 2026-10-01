package io.pipecrafts.prototype.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.exactly;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static io.pipecrafts.prototype.integration.demo.DemoClients.ACCOUNT;
import static io.pipecrafts.prototype.integration.demo.DemoClients.NOTIFICATION;
import static io.pipecrafts.prototype.integration.demo.DemoClients.PROFILE;
import static io.pipecrafts.prototype.integration.demo.DemoClients.VERIFICATION;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.ResponseEntity;

import com.github.tomakehurst.wiremock.client.ResponseDefinitionBuilder;
import com.github.tomakehurst.wiremock.http.Fault;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreaker.State;
import io.pipecrafts.prototype.integration.support.ResilientIntegrationTestBase;
import tools.jackson.databind.JsonNode;

/** Executable documentation of the resilient client pattern; numbered scenarios are indexed in the README. */
@ExtendWith(OutputCaptureExtension.class)
@DisplayName("Resilient REST client")
class ResilientRestClientIntegrationTest extends ResilientIntegrationTestBase {

    private static final String BALANCE = "/demo/accounts/A1/balance";
    private static final String VERIFY = "/demo/verifications/C1/verify";
    private static final String NOTIFY = "/demo/notifications";
    private static final String NOTIFY_BODY = """
            {"recipient":"customer-1","channel":"SMS","message":"Your code is ready"}""";

    private static final String PROFILE_JSON = """
            {"id":"P1","name":"Juan Dela Cruz","mobile":"09170000001"}""";
    private static final String BALANCE_JSON = """
            {"accountId":"A1","amount":1500.25,"currency":"PHP"}""";
    private static final String RECEIPT_JSON = """
            {"notificationId":"N1","status":"ACCEPTED"}""";

    private static ResponseDefinitionBuilder error(int status, String code, String message) {
        return aResponse().withStatus(status).withHeader("Content-Type", "application/json")
                .withBody("""
                        {"code":"%s","message":"%s"}""".formatted(code, message));
    }

    private void stubBalance(ResponseDefinitionBuilder response) {
        accountServer.stubFor(get(urlEqualTo("/accounts/A1/balance")).willReturn(response));
    }

    private int balanceRequests() {
        return accountServer.countRequestsMatching(getRequestedFor(urlEqualTo("/accounts/A1/balance")).build())
                .getCount();
    }

    private void stubVerify(ResponseDefinitionBuilder response) {
        verificationServer.stubFor(post(urlEqualTo("/challenges/C1/verify")).willReturn(response));
    }

    private ResponseEntity<String> verify(String code) {
        return apiPost(VERIFY, """
                {"code":"%s"}""".formatted(code));
    }

    private void openAccountBreakerWithServerErrors() {
        stubBalance(aResponse().withStatus(503));
        for (int i = 0; i < 4; i++) {
            assertProblem(apiGet(BALANCE), 502, "DEPENDENCY_FAILED");
        }
        assertThat(breaker(ACCOUNT).getState()).isEqualTo(State.OPEN);
    }

    private static void assertUntouchedByFailures(CircuitBreaker breaker) {
        assertThat(breaker.getState()).isEqualTo(State.CLOSED);
        assertThat(breaker.getMetrics().getNumberOfFailedCalls()).isZero();
    }

    @Nested
    @DisplayName("Happy path")
    class HappyPath {

        @Test
        @DisplayName("1. profile endpoints return 200 with the downstream payload mapped")
        void profileEndpoints() {
            profileServer.stubFor(get(urlEqualTo("/profiles/P1")).willReturn(okJson(PROFILE_JSON)));
            profileServer.stubFor(get(urlEqualTo("/profiles?mobile=09170000001")).willReturn(okJson(PROFILE_JSON)));

            ResponseEntity<String> byId = apiGet("/demo/profiles/P1");
            ResponseEntity<String> byMobile = apiGet("/demo/profiles?mobile=09170000001");

            assertThat(byId.getStatusCode().value()).isEqualTo(200);
            assertThat(json(byId).path("name").asString()).isEqualTo("Juan Dela Cruz");
            assertThat(byMobile.getStatusCode().value()).isEqualTo(200);
            assertThat(json(byMobile).path("id").asString()).isEqualTo("P1");
        }

        @Test
        @DisplayName("1. verification endpoints return 200 with the downstream payload mapped")
        void verificationEndpoints() {
            verificationServer.stubFor(post(urlEqualTo("/challenges"))
                    .withRequestBody(equalToJson("""
                            {"customerId":"CUST-1","channel":"SMS"}"""))
                    .willReturn(okJson("""
                            {"challengeId":"C1","channel":"SMS","expiresAt":"2026-10-01T10:00:00Z"}""")));
            stubVerify(okJson("""
                    {"challengeId":"C1","status":"VERIFIED"}"""));

            ResponseEntity<String> started = apiPost("/demo/verifications", """
                    {"customerId":"CUST-1","channel":"SMS"}""");
            ResponseEntity<String> verified = verify("123456");

            assertThat(started.getStatusCode().value()).isEqualTo(200);
            assertThat(json(started).path("challengeId").asString()).isEqualTo("C1");
            assertThat(json(started).path("expiresAt").asString()).isEqualTo("2026-10-01T10:00:00Z");
            assertThat(verified.getStatusCode().value()).isEqualTo(200);
            assertThat(json(verified).path("status").asString()).isEqualTo("VERIFIED");
            verificationServer.verify(postRequestedFor(urlEqualTo("/challenges/C1/verify"))
                    .withRequestBody(equalToJson("""
                            {"code":"123456"}""")));
        }

        @Test
        @DisplayName("1. account endpoints return 200 with the downstream payload mapped")
        void accountEndpoints() {
            stubBalance(okJson(BALANCE_JSON));
            accountServer.stubFor(get(urlEqualTo("/accounts/A1/transactions")).willReturn(okJson("""
                    {"accountId":"A1","items":[{"id":"T1","amount":-20.00,"description":"Coffee"}]}""")));

            ResponseEntity<String> balance = apiGet(BALANCE);
            ResponseEntity<String> transactions = apiGet("/demo/accounts/A1/transactions");

            assertThat(balance.getStatusCode().value()).isEqualTo(200);
            assertThat(json(balance).path("amount").decimalValue()).isEqualByComparingTo("1500.25");
            assertThat(transactions.getStatusCode().value()).isEqualTo(200);
            assertThat(json(transactions).path("items").get(0).path("description").asString()).isEqualTo("Coffee");
        }

        @Test
        @DisplayName("1. notification endpoints return 200 with the downstream payload mapped")
        void notificationEndpoints() {
            notificationServer.stubFor(post(urlEqualTo("/notifications")).willReturn(okJson(RECEIPT_JSON)));
            notificationServer.stubFor(get(urlEqualTo("/notifications/N1/status")).willReturn(okJson("""
                    {"notificationId":"N1","status":"DELIVERED"}""")));

            ResponseEntity<String> sent = apiPost(NOTIFY, NOTIFY_BODY);
            ResponseEntity<String> status = apiGet("/demo/notifications/N1/status");

            assertThat(sent.getStatusCode().value()).isEqualTo(200);
            assertThat(json(sent).path("notificationId").asString()).isEqualTo("N1");
            assertThat(status.getStatusCode().value()).isEqualTo(200);
            assertThat(json(status).path("status").asString()).isEqualTo("DELIVERED");
        }
    }

    @Nested
    @DisplayName("Request validation")
    class RequestValidation {

        @Test
        @DisplayName("Invalid requests yield 400 INVALID_REQUEST without calling the downstream")
        void invalidRequests() {
            assertProblem(apiGet("/demo/profiles"), 400, "INVALID_REQUEST");
            assertProblem(apiGet("/demo/profiles?mobile= "), 400, "INVALID_REQUEST");
            assertProblem(verify(""), 400, "INVALID_REQUEST");
            assertProblem(apiPost(VERIFY, "{not json"), 400, "INVALID_REQUEST");

            profileServer.verify(exactly(0), getRequestedFor(urlPathEqualTo("/profiles")));
            verificationServer.verify(exactly(0), postRequestedFor(urlEqualTo("/challenges/C1/verify")));
        }
    }

    @Nested
    @DisplayName("Business failures (breaker stays CLOSED)")
    class BusinessFailures {

        @Test
        @DisplayName("2. profile 404 yields API 404 PROFILE_NOT_FOUND")
        void profileNotFound() {
            profileServer.stubFor(get(urlEqualTo("/profiles/P404")).willReturn(error(404, "NOT_FOUND", "no such")));

            assertProblem(apiGet("/demo/profiles/P404"), 404, "PROFILE_NOT_FOUND");
            assertUntouchedByFailures(breaker(PROFILE));
        }

        @Test
        @DisplayName("3. verification wrong code (422 CODE_INCORRECT) yields the facade's business outcome")
        void wrongCode() {
            stubVerify(error(422, "CODE_INCORRECT", "wrong code"));

            assertProblem(verify("000000"), 422, "VERIFICATION_CODE_INCORRECT");
            assertUntouchedByFailures(breaker(VERIFICATION));
        }

        @Test
        @DisplayName("4. verification expired code (422 CODE_EXPIRED) yields a different outcome than a wrong code")
        void expiredCode() {
            stubVerify(error(422, "CODE_EXPIRED", "expired"));

            assertProblem(verify("000000"), 422, "VERIFICATION_CODE_EXPIRED");
            assertUntouchedByFailures(breaker(VERIFICATION));
        }

        @Test
        @DisplayName("5. unmapped downstream 4xx (400) yields the generic DOWNSTREAM_REJECTED")
        void unmappedBusinessError() {
            verificationServer.stubFor(post(urlEqualTo("/challenges"))
                    .willReturn(error(400, "CUSTOMER_BLOCKED", "internal detail that must not leak")));

            ResponseEntity<String> response = apiPost("/demo/verifications", """
                    {"customerId":"CUST-1","channel":"SMS"}""");

            assertProblem(response, 422, "DOWNSTREAM_REJECTED");
            assertThat(response.getBody()).doesNotContain("CUSTOMER_BLOCKED", "must not leak", "400");
            assertUntouchedByFailures(breaker(VERIFICATION));
        }

        @Test
        @DisplayName("5. an unmapped business code passes through the helper as DOWNSTREAM_REJECTED")
        void unmappedCodeThroughHelper() {
            stubVerify(error(422, "ATTEMPTS_EXHAUSTED", "too many tries"));

            assertProblem(verify("000000"), 422, "DOWNSTREAM_REJECTED");
        }

        @Test
        @DisplayName("6. more business failures than the window size never open the breaker; success still works")
        void manyBusinessFailures() {
            profileServer.stubFor(get(urlEqualTo("/profiles/P404")).willReturn(error(404, "NOT_FOUND", "no such")));
            profileServer.stubFor(get(urlEqualTo("/profiles/P1")).willReturn(okJson(PROFILE_JSON)));

            for (int i = 0; i < 10; i++) {
                assertProblem(apiGet("/demo/profiles/P404"), 404, "PROFILE_NOT_FOUND");
            }

            assertUntouchedByFailures(breaker(PROFILE));
            assertThat(apiGet("/demo/profiles/P1").getStatusCode().value()).isEqualTo(200);
            profileServer.verify(exactly(10), getRequestedFor(urlEqualTo("/profiles/P404")));
        }

        @Test
        @DisplayName("7. non-JSON and empty error bodies do not break classification")
        void tolerantErrorBodies() {
            profileServer.stubFor(get(urlEqualTo("/profiles/HTML")).willReturn(aResponse().withStatus(404)
                    .withHeader("Content-Type", "text/html").withBody("<html><body>Not Found</body></html>")));
            profileServer.stubFor(get(urlEqualTo("/profiles/EMPTY")).willReturn(aResponse().withStatus(404)));
            profileServer.stubFor(get(urlPathEqualTo("/profiles")).willReturn(aResponse().withStatus(400)
                    .withHeader("Content-Type", "application/json").withBody("{not json")));

            assertProblem(apiGet("/demo/profiles/HTML"), 404, "PROFILE_NOT_FOUND");
            assertProblem(apiGet("/demo/profiles/EMPTY"), 404, "PROFILE_NOT_FOUND");
            assertProblem(apiGet("/demo/profiles?mobile=09170000001"), 422, "DOWNSTREAM_REJECTED");
            assertUntouchedByFailures(breaker(PROFILE));
        }
    }

    @Nested
    @DisplayName("Dependency failures (all fold into the identical 502)")
    class DependencyFailures {

        @ParameterizedTest(name = "8. downstream {0} yields 502 DEPENDENCY_FAILED")
        @ValueSource(ints = {500, 503, 504})
        void serverErrors(int status, CapturedOutput output) {
            stubBalance(error(status, "DB_DOWN", "stack trace from downstream"));

            ResponseEntity<String> response = apiGet(BALANCE);

            assertProblem(response, 502, "DEPENDENCY_FAILED");
            assertThat(response.getBody()).doesNotContain("DB_DOWN", "stack trace", String.valueOf(status));
            assertThat(output).contains("reason=SERVER_ERROR", "httpStatus=" + status);
        }

        @Test
        @DisplayName("9. connection reset yields 502 with reason CONNECTION_FAILED")
        void connectionReset(CapturedOutput output) {
            stubBalance(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER));

            assertProblem(apiGet(BALANCE), 502, "DEPENDENCY_FAILED");
            assertThat(output).contains("reason=CONNECTION_FAILED");
        }

        @Test
        @DisplayName("9. read timeout (delay above the client's read timeout) yields 502 with reason TIMEOUT")
        void readTimeout(CapturedOutput output) {
            profileServer.stubFor(get(urlEqualTo("/profiles/SLOW"))
                    .willReturn(okJson(PROFILE_JSON).withFixedDelay(1_000)));

            assertProblem(apiGet("/demo/profiles/SLOW"), 502, "DEPENDENCY_FAILED");
            assertThat(output).contains("dependency=profile-service", "reason=TIMEOUT");
        }

        @ParameterizedTest(name = "10. downstream {0} yields the same 502 with reason UNAUTHORIZED")
        @ValueSource(ints = {401, 403})
        void rejectedCredentials(int status, CapturedOutput output) {
            stubVerify(aResponse().withStatus(503));
            String outageBody = verify("123456").getBody();
            stubVerify(error(status, "BAD_TOKEN", "token expired"));

            ResponseEntity<String> response = verify("123456");

            assertProblem(response, 502, "DEPENDENCY_FAILED");
            assertThat(response.getBody()).isEqualTo(outageBody);
            assertThat(output).contains("outcome=DEPENDENCY_REJECTED reason=UNAUTHORIZED httpStatus=" + status);
        }

        @Test
        @DisplayName("11. account 429 is a dependency failure with reason THROTTLED, sent exactly once")
        void throttled(CapturedOutput output) {
            stubBalance(aResponse().withStatus(429).withHeader("Retry-After", "1"));

            long started = System.nanoTime();
            ResponseEntity<String> response = apiGet(BALANCE);
            Duration elapsed = Duration.ofNanos(System.nanoTime() - started);

            assertProblem(response, 502, "DEPENDENCY_FAILED");
            assertThat(output).contains("reason=THROTTLED");
            assertThat(balanceRequests()).as("no hidden transport retry").isEqualTo(1);
            assertThat(elapsed).isLessThan(Duration.ofMillis(900));
            assertThat(breaker(ACCOUNT).getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        }

        @Test
        @DisplayName("11. a 503 on a non-idempotent POST is sent exactly once")
        void noHiddenRetryOnPost() {
            notificationServer.stubFor(post(urlEqualTo("/notifications")).willReturn(aResponse().withStatus(503)));

            assertProblem(apiPost(NOTIFY, NOTIFY_BODY), 502, "DEPENDENCY_FAILED");
            notificationServer.verify(exactly(1), postRequestedFor(urlEqualTo("/notifications")));
        }

        @Test
        @DisplayName("12. the 502 body is byte-for-byte identical for every reason (no leakage)")
        void identicalBodies(CapturedOutput output) {
            List<ResponseDefinitionBuilder> failures = List.of(
                    error(500, "E500", "secret detail 500"),
                    error(503, "E503", "secret detail 503"),
                    error(504, "E504", "secret detail 504"),
                    aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER),
                    okJson(BALANCE_JSON).withFixedDelay(2_500),
                    error(401, "E401", "secret detail 401"),
                    error(403, "E403", "secret detail 403"),
                    error(429, "E429", "secret detail 429"),
                    okJson("{\"accountId\": \"A1\", \"amount\": "));

            List<String> bodies = new ArrayList<>();
            for (ResponseDefinitionBuilder failure : failures) {
                breaker(ACCOUNT).reset();
                stubBalance(failure);
                ResponseEntity<String> response = apiGet(BALANCE);
                assertThat(response.getStatusCode().value()).isEqualTo(502);
                bodies.add(response.getBody());
            }
            breaker(ACCOUNT).transitionToOpenState();
            bodies.add(apiGet(BALANCE).getBody());

            assertThat(bodies).hasSize(10).allSatisfy(body -> assertThat(body).isEqualTo(bodies.getFirst()));
            assertThat(bodies.getFirst()).doesNotContain("secret", "E5", "E4", "reason");
            assertThat(output).contains("reason=SERVER_ERROR", "reason=CONNECTION_FAILED", "reason=TIMEOUT",
                    "reason=UNAUTHORIZED", "reason=THROTTLED", "reason=MALFORMED_RESPONSE", "reason=CIRCUIT_OPEN");
        }
    }

    @Nested
    @DisplayName("Circuit breaker")
    class CircuitBreakerBehaviour {

        @Test
        @DisplayName("13. consecutive 5xx open the breaker; the next call fails fast without reaching the downstream")
        void opensAndFailsFast() {
            openAccountBreakerWithServerErrors();

            assertProblem(apiGet(BALANCE), 502, "DEPENDENCY_FAILED");

            accountServer.verify(exactly(4), getRequestedFor(urlEqualTo("/accounts/A1/balance")));
        }

        @Test
        @DisplayName("14. after the wait duration a successful half-open probe closes the breaker")
        void halfOpenProbeSucceeds() {
            openAccountBreakerWithServerErrors();
            stubBalance(okJson(BALANCE_JSON));

            await().pollDelay(Duration.ofMillis(900)).pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5))
                    .until(() -> apiGet(BALANCE).getStatusCode().value() == 200);

            assertThat(breaker(ACCOUNT).getState()).isEqualTo(State.CLOSED);
            assertThat(balanceRequests()).as("4 failures + 1 probe").isEqualTo(5);
        }

        @Test
        @DisplayName("14. after the wait duration a failing half-open probe reopens the breaker")
        void halfOpenProbeFails() {
            openAccountBreakerWithServerErrors();

            await().pollDelay(Duration.ofMillis(900)).pollInterval(Duration.ofMillis(100)).atMost(Duration.ofSeconds(5))
                    .until(() -> {
                        apiGet(BALANCE);
                        return balanceRequests() == 5;
                    });

            assertThat(breaker(ACCOUNT).getState()).isEqualTo(State.OPEN);
            assertProblem(apiGet(BALANCE), 502, "DEPENDENCY_FAILED");
            assertThat(balanceRequests()).as("reopened breaker fails fast again").isEqualTo(5);
        }

        @Test
        @DisplayName("15. opening account-service does not affect profile-service")
        void isolation() {
            openAccountBreakerWithServerErrors();
            profileServer.stubFor(get(urlEqualTo("/profiles/P1")).willReturn(okJson(PROFILE_JSON)));

            assertThat(apiGet("/demo/profiles/P1").getStatusCode().value()).isEqualTo(200);
            assertThat(breaker(PROFILE).getState()).isEqualTo(State.CLOSED);
        }

        @Test
        @DisplayName("16. slow calls above slowCallDurationThreshold open the breaker even with 200s")
        void slowCallsOpenBreaker() {
            stubBalance(okJson(BALANCE_JSON).withFixedDelay(500));

            for (int i = 0; i < 4; i++) {
                assertThat(apiGet(BALANCE).getStatusCode().value()).isEqualTo(200);
            }

            assertThat(breaker(ACCOUNT).getState()).isEqualTo(State.OPEN);
            assertProblem(apiGet(BALANCE), 502, "DEPENDENCY_FAILED");
            assertThat(balanceRequests()).isEqualTo(4);
        }

        @Test
        @DisplayName("18. actuator exposes breaker state without failing the application's health")
        void actuatorShowsState() {
            openAccountBreakerWithServerErrors();

            JsonNode breakers = json(apiGet("/actuator/circuitbreakers")).path("circuitBreakers");
            assertThat(breakers.path(ACCOUNT).path("state").asString()).isEqualTo("OPEN");
            assertThat(breakers.path(PROFILE).path("state").asString()).isEqualTo("CLOSED");

            ResponseEntity<String> health = apiGet("/actuator/health");
            assertThat(health.getStatusCode().value()).isEqualTo(200);
            assertThat(json(health).path("status").asString()).isEqualTo("UP");
            assertThat(json(health).path("components").path("circuitBreakers").path("details").path(ACCOUNT)
                    .path("details").path("state").asString()).isEqualTo("OPEN");
        }
    }

    @Nested
    @DisplayName("Escape hatches")
    class EscapeHatches {

        @Test
        @DisplayName("19. notification's classifier treats 409 as success in both the handler and the breaker")
        void classifierOverride() {
            notificationServer.stubFor(post(urlEqualTo("/notifications")).willReturn(aResponse().withStatus(409)
                    .withHeader("Content-Type", "application/json").withBody("""
                            {"notificationId":"N1","status":"DUPLICATE_ACCEPTED"}""")));

            ResponseEntity<String> response = apiPost(NOTIFY, NOTIFY_BODY);

            assertThat(response.getStatusCode().value()).isEqualTo(200);
            assertThat(json(response).path("status").asString()).isEqualTo("DUPLICATE_ACCEPTED");
            CircuitBreaker.Metrics metrics = breaker(NOTIFICATION).getMetrics();
            assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);
            assertThat(metrics.getNumberOfFailedCalls()).isZero();
        }

        @Test
        @DisplayName("20. notification's customizer header is sent, and only to notification-service")
        void customizerHeader() {
            notificationServer.stubFor(post(urlEqualTo("/notifications")).willReturn(okJson(RECEIPT_JSON)));
            profileServer.stubFor(get(urlEqualTo("/profiles/P1")).willReturn(okJson(PROFILE_JSON)));

            apiPost(NOTIFY, NOTIFY_BODY);
            apiGet("/demo/profiles/P1");

            notificationServer.verify(postRequestedFor(urlEqualTo("/notifications"))
                    .withHeader("X-Api-Key", equalTo("test-api-key"))
                    .withHeader("Efs-Product-Id", equalTo("prototype-integrations")));
            profileServer.verify(getRequestedFor(urlEqualTo("/profiles/P1")).withoutHeader("X-Api-Key"));
        }

        @Test
        @DisplayName("21. profile 404 is mapped per call on /{id} but propagates as DOWNSTREAM_REJECTED on ?mobile=")
        void perCallBusinessMapping() {
            profileServer.stubFor(get(urlPathEqualTo("/profiles/P404")).willReturn(error(404, "NOT_FOUND", "no")));
            profileServer.stubFor(get(urlPathEqualTo("/profiles")).willReturn(error(404, "NOT_FOUND", "no")));

            assertProblem(apiGet("/demo/profiles/P404"), 404, "PROFILE_NOT_FOUND");
            assertProblem(apiGet("/demo/profiles?mobile=09170000009"), 422, "DOWNSTREAM_REJECTED");
        }

        @Test
        @DisplayName("22. repeated 401s open notification's breaker (override) but not profile's (default)")
        void rejectedCountsAsFailureOnlyWhenConfigured() {
            notificationServer.stubFor(get(urlEqualTo("/notifications/N1/status")).willReturn(aResponse().withStatus(401)));
            profileServer.stubFor(get(urlEqualTo("/profiles/P1")).willReturn(aResponse().withStatus(401)));

            List<ResponseEntity<String>> responses = new ArrayList<>();
            for (int i = 0; i < 6; i++) {
                responses.add(apiGet("/demo/notifications/N1/status"));
                responses.add(apiGet("/demo/profiles/P1"));
            }

            assertThat(breaker(NOTIFICATION).getState()).isEqualTo(State.OPEN);
            assertThat(breaker(PROFILE).getState()).isEqualTo(State.CLOSED);
            assertThat(breaker(PROFILE).getMetrics().getNumberOfBufferedCalls()).as("401 ignored, not recorded").isZero();
            notificationServer.verify(exactly(4), getRequestedFor(urlEqualTo("/notifications/N1/status")));
            profileServer.verify(exactly(6), getRequestedFor(urlEqualTo("/profiles/P1")));
            assertThat(responses).allSatisfy(response -> {
                assertProblem(response, 502, "DEPENDENCY_FAILED");
                JsonNode body = json(response);
                assertThat(body.path("detail").asString()).isEqualTo(json(responses.getFirst()).path("detail").asString());
                assertThat(body.path("title").asString()).isEqualTo(json(responses.getFirst()).path("title").asString());
            });
        }
    }
}

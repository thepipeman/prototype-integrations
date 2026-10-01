package io.pipecrafts.prototype.integration.web;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.assertj.MockMvcTester;
import org.springframework.test.web.servlet.assertj.MvcTestResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.pipecrafts.prototype.integration.failure.DependencyFailedException;
import io.pipecrafts.prototype.integration.failure.DownstreamBusinessException;
import io.pipecrafts.prototype.integration.failure.DownstreamError;
import io.pipecrafts.prototype.integration.failure.FailureCategory;
import io.pipecrafts.prototype.integration.failure.FailureReason;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

class ApiExceptionHandlerTest {

    private final MockMvcTester mvc = MockMvcTester.of(List.of(new FailingController()),
            builder -> builder.setControllerAdvice(new ApiExceptionHandler()).build());

    @RestController
    static class FailingController {

        @GetMapping("/dependency")
        String dependency(@RequestParam FailureReason reason, @RequestParam(required = false) Integer status) {
            FailureCategory category = reason == FailureReason.UNAUTHORIZED ? FailureCategory.DEPENDENCY_REJECTED
                    : FailureCategory.DEPENDENCY_FAILURE;
            throw new DependencyFailedException("account-service", "AccountClient#getBalance",
                    status == null ? null : HttpStatusCode.valueOf(status), category, reason, "secret body", null);
        }

        @GetMapping("/business")
        String business(@RequestParam int status, @RequestParam String code) {
            throw new DownstreamBusinessException("verification-service", "VerificationClient#verify",
                    HttpStatusCode.valueOf(status), new DownstreamError(code, "secret message"), "secret body");
        }

        @GetMapping("/api")
        String api() {
            throw new ApiException(ApiErrorCode.PROFILE_NOT_FOUND);
        }

        @GetMapping("/bug")
        String bug() {
            throw new IllegalStateException("secret internal state");
        }

        @PostMapping("/validated")
        String validated(@Valid @RequestBody Payload payload) {
            return payload.name();
        }

        record Payload(@NotBlank String name) {
        }
    }

    private String body(MvcTestResult result) {
        return new String(result.getResponse().getContentAsByteArray(), UTF_8);
    }

    @Test
    void everyDependencyFailureReasonSerializesToTheIdenticalBody() {
        List<String> bodies = Arrays.stream(FailureReason.values())
                .map(reason -> mvc.get().uri("/dependency?reason={reason}&status=503", reason).exchange())
                .peek(result -> assertThat(result).hasStatus(502)
                        .hasContentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .map(this::body)
                .toList();

        assertThat(bodies).allSatisfy(body -> assertThat(body).isEqualTo(bodies.getFirst()));
        assertThat(bodies.getFirst())
                .contains("\"code\":\"DEPENDENCY_FAILED\"")
                .doesNotContain("secret", "account-service", "503", "SERVER_ERROR", "reason");
    }

    @Test
    void unmappedBusinessFailureIsAConstantDownstreamRejected() {
        MvcTestResult notFound = mvc.get().uri("/business?status=404&code=NOT_FOUND").exchange();
        MvcTestResult conflict = mvc.get().uri("/business?status=409&code=DUPLICATE").exchange();

        assertThat(notFound).hasStatus(422);
        assertThat(body(notFound)).isEqualTo(body(conflict))
                .contains("\"code\":\"DOWNSTREAM_REJECTED\"")
                .doesNotContain("secret", "NOT_FOUND", "404");
    }

    @Test
    void apiExceptionUsesItsOwnCode() {
        assertThat(mvc.get().uri("/api")).hasStatus(404)
                .bodyJson().extractingPath("$.code").isEqualTo("PROFILE_NOT_FOUND");
    }

    @Test
    void unexpectedExceptionIsAConstantInternalError() {
        MvcTestResult result = mvc.get().uri("/bug").exchange();

        assertThat(result).hasStatus(500).bodyJson().extractingPath("$.code").isEqualTo("INTERNAL_ERROR");
        assertThat(body(result)).doesNotContain("secret");
    }

    @Test
    void springMvcValidationErrorsCarryInvalidRequest() {
        assertThat(mvc.post().uri("/validated").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"\"}"))
                .hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        assertThat(mvc.post().uri("/validated").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
        assertThat(mvc.get().uri("/dependency"))
                .hasStatus(400).bodyJson().extractingPath("$.code").isEqualTo("INVALID_REQUEST");
    }
}

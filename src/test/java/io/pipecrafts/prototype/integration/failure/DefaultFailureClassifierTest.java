package io.pipecrafts.prototype.integration.failure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpStatusCode;

class DefaultFailureClassifierTest {

    @ParameterizedTest(name = "{0} -> {1} / breaker {2} / reason {3}")
    @CsvSource({
            "200, SUCCESS,             SUCCESS, ",
            "201, SUCCESS,             SUCCESS, ",
            "204, SUCCESS,             SUCCESS, ",
            "400, BUSINESS,            SUCCESS, ",
            "404, BUSINESS,            SUCCESS, ",
            "409, BUSINESS,            SUCCESS, ",
            "422, BUSINESS,            SUCCESS, ",
            "401, DEPENDENCY_REJECTED, IGNORED, UNAUTHORIZED",
            "403, DEPENDENCY_REJECTED, IGNORED, UNAUTHORIZED",
            "408, DEPENDENCY_FAILURE,  FAILURE, TIMEOUT",
            "429, DEPENDENCY_FAILURE,  FAILURE, THROTTLED",
            "500, DEPENDENCY_FAILURE,  FAILURE, SERVER_ERROR",
            "502, DEPENDENCY_FAILURE,  FAILURE, SERVER_ERROR",
            "503, DEPENDENCY_FAILURE,  FAILURE, SERVER_ERROR",
            "504, DEPENDENCY_FAILURE,  FAILURE, SERVER_ERROR",
            "599, DEPENDENCY_FAILURE,  FAILURE, SERVER_ERROR",
            "100, DEPENDENCY_FAILURE,  FAILURE, SERVER_ERROR",
            "302, DEPENDENCY_FAILURE,  FAILURE, SERVER_ERROR"
    })
    void standardTaxonomy(int status, FailureCategory category, BreakerOutcome outcome, @Nullable FailureReason reason) {
        Classification classification = DefaultFailureClassifier.standard().classify(HttpStatusCode.valueOf(status));

        assertThat(classification).isEqualTo(new Classification(category, outcome, reason));
    }

    @Test
    void rejectedCredentialsCanCountAsBreakerFailure() {
        DefaultFailureClassifier classifier = DefaultFailureClassifier.builder().countRejectedAsFailure(true).build();

        assertThat(classifier.classify(HttpStatusCode.valueOf(401)))
                .isEqualTo(Classification.dependencyRejected(BreakerOutcome.FAILURE));
        assertThat(classifier.classify(HttpStatusCode.valueOf(403)).breakerOutcome()).isEqualTo(BreakerOutcome.FAILURE);
        assertThat(classifier.classify(HttpStatusCode.valueOf(404))).isEqualTo(Classification.business());
    }

    @Test
    void overrideWinsOverDefaultAndOnlyForItsStatus() {
        DefaultFailureClassifier classifier = DefaultFailureClassifier.builder()
                .override(409, Classification.success())
                .override(401, Classification.dependencyFailure(FailureReason.SERVER_ERROR))
                .build();

        assertThat(classifier.classify(HttpStatusCode.valueOf(409))).isEqualTo(Classification.success());
        assertThat(classifier.classify(HttpStatusCode.valueOf(401)).category())
                .isEqualTo(FailureCategory.DEPENDENCY_FAILURE);
        assertThat(classifier.classify(HttpStatusCode.valueOf(422))).isEqualTo(Classification.business());
    }

    @Test
    void classificationRejectsInconsistentReason() {
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Classification(FailureCategory.BUSINESS, BreakerOutcome.SUCCESS, FailureReason.TIMEOUT));
        assertThatIllegalArgumentException().isThrownBy(
                () -> new Classification(FailureCategory.DEPENDENCY_FAILURE, BreakerOutcome.FAILURE, null));
    }
}

package io.pipecrafts.prototype.integration.call;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicInteger;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatusCode;

import io.pipecrafts.prototype.integration.failure.DependencyFailedException;
import io.pipecrafts.prototype.integration.failure.DownstreamBusinessException;
import io.pipecrafts.prototype.integration.failure.DownstreamError;
import io.pipecrafts.prototype.integration.failure.FailureCategory;
import io.pipecrafts.prototype.integration.failure.FailureReason;

class DownstreamCallTest {

    private static DownstreamBusinessException business(int status, @Nullable String code) {
        return new DownstreamBusinessException("verification-service", "VerificationClient#verify",
                HttpStatusCode.valueOf(status), new DownstreamError(code, null), null);
    }

    @Test
    void isLazyAndReturnsTheSuccessValue() {
        AtomicInteger invocations = new AtomicInteger();
        DownstreamCall<String> call = Downstream.call(() -> "ok-" + invocations.incrementAndGet());

        assertThat(invocations).hasValue(0);
        assertThat(call.get()).isEqualTo("ok-1");
    }

    @Test
    void firstMatchingMappingWinsInDeclarationOrder() {
        String outcome = Downstream.<String>call(() -> {
                    throw business(422, "CODE_EXPIRED");
                })
                .onBusiness(422, () -> "by-status")
                .onBusinessCode("CODE_EXPIRED", () -> "by-code")
                .get();

        assertThat(outcome).isEqualTo("by-status");
    }

    @Test
    void mapsByCode() {
        String outcome = Downstream.<String>call(() -> {
                    throw business(422, "CODE_EXPIRED");
                })
                .onBusinessCode("CODE_INCORRECT", () -> "incorrect")
                .onBusinessCode("CODE_EXPIRED", () -> "expired")
                .get();

        assertThat(outcome).isEqualTo("expired");
    }

    @Test
    void unmatchedBusinessFailureIsRethrownAsTheSameInstance() {
        DownstreamBusinessException unmapped = business(422, "ATTEMPTS_EXHAUSTED");

        DownstreamCall<String> call = Downstream.<String>call(() -> {
                    throw unmapped;
                })
                .onBusinessCode("CODE_EXPIRED", () -> "expired")
                .onBusinessCode("CODE_INCORRECT", () -> "incorrect");

        assertThatThrownBy(call::get).isSameAs(unmapped);
    }

    @Test
    void missingCodeNeverMatchesACodeMapping() {
        DownstreamBusinessException noCode = business(422, null);

        assertThatThrownBy(Downstream.<String>call(() -> {
            throw noCode;
        }).onBusinessCode("CODE_EXPIRED", () -> "expired")::get).isSameAs(noCode);
    }

    @Test
    void dependencyFailuresAreNeverSwallowedEvenByACatchAllMapping() {
        DependencyFailedException outage = new DependencyFailedException("verification-service", "op",
                HttpStatusCode.valueOf(503), FailureCategory.DEPENDENCY_FAILURE, FailureReason.SERVER_ERROR, null,
                null);

        DownstreamCall<String> call = Downstream.<String>call(() -> {
                    throw outage;
                })
                .onBusiness(ex -> true, ex -> "swallowed");

        assertThatThrownBy(call::get).isSameAs(outage);
    }

    @Test
    void addingAMappingDoesNotChangeTheOriginalCall() {
        DownstreamBusinessException notFound = business(404, null);
        DownstreamCall<String> base = Downstream.call(() -> {
            throw notFound;
        });

        DownstreamCall<String> mapped = base.onBusiness(404, () -> "empty");

        assertThat(mapped.get()).isEqualTo("empty");
        assertThatThrownBy(base::get).isSameAs(notFound);
    }
}

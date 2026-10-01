package io.pipecrafts.prototype.integration.failure;

import static java.util.Objects.requireNonNull;

import org.jspecify.annotations.Nullable;

/** How one downstream HTTP status is treated by both the circuit breaker and the status handler. */
public record Classification(FailureCategory category, BreakerOutcome breakerOutcome, @Nullable FailureReason reason) {

    public Classification {
        requireNonNull(category, "category");
        requireNonNull(breakerOutcome, "breakerOutcome");
        boolean dependencyCategory = category == FailureCategory.DEPENDENCY_FAILURE
                || category == FailureCategory.DEPENDENCY_REJECTED;
        if (dependencyCategory != (reason != null)) {
            throw new IllegalArgumentException(
                    "A reason is required for dependency categories and forbidden otherwise: " + category);
        }
    }

    public static Classification success() {
        return new Classification(FailureCategory.SUCCESS, BreakerOutcome.SUCCESS, null);
    }

    public static Classification business() {
        return new Classification(FailureCategory.BUSINESS, BreakerOutcome.SUCCESS, null);
    }

    public static Classification dependencyFailure(FailureReason reason) {
        return new Classification(FailureCategory.DEPENDENCY_FAILURE, BreakerOutcome.FAILURE, reason);
    }

    public static Classification dependencyRejected(BreakerOutcome breakerOutcome) {
        return new Classification(FailureCategory.DEPENDENCY_REJECTED, breakerOutcome, FailureReason.UNAUTHORIZED);
    }
}

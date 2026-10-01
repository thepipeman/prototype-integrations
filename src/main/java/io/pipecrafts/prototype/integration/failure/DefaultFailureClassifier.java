package io.pipecrafts.prototype.integration.failure;

import static io.pipecrafts.prototype.integration.failure.Classification.business;
import static io.pipecrafts.prototype.integration.failure.Classification.dependencyFailure;
import static io.pipecrafts.prototype.integration.failure.Classification.dependencyRejected;
import static io.pipecrafts.prototype.integration.failure.Classification.success;
import static java.util.Objects.requireNonNull;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatusCode;

/**
 * Default taxonomy: 2xx success; 401/403 rejected (breaker-ignored unless configured otherwise); 408 and 429
 * dependency failures; other 4xx business; everything else (5xx, 1xx, unfollowed 3xx) a dependency failure.
 *
 * <p>401/403 are ignored by the breaker by default: a credentials fault says nothing about availability, and
 * counting it as success would dilute a concurrent real outage.
 */
public final class DefaultFailureClassifier implements FailureClassifier {

    private static final DefaultFailureClassifier STANDARD = builder().build();

    private final boolean countRejectedAsFailure;
    private final Map<Integer, Classification> overrides;

    private DefaultFailureClassifier(boolean countRejectedAsFailure, Map<Integer, Classification> overrides) {
        this.countRejectedAsFailure = countRejectedAsFailure;
        this.overrides = Map.copyOf(overrides);
    }

    public static DefaultFailureClassifier standard() {
        return STANDARD;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public Classification classify(HttpStatusCode status) {
        Classification override = overrides.get(status.value());
        if (override != null) {
            return override;
        }
        return switch (status.value()) {
            case 401, 403 -> dependencyRejected(countRejectedAsFailure ? BreakerOutcome.FAILURE : BreakerOutcome.IGNORED);
            case 408 -> dependencyFailure(FailureReason.TIMEOUT);
            case 429 -> dependencyFailure(FailureReason.THROTTLED);
            default -> {
                if (status.is2xxSuccessful()) {
                    yield success();
                }
                if (status.is4xxClientError()) {
                    yield business();
                }
                yield dependencyFailure(FailureReason.SERVER_ERROR);
            }
        };
    }

    public static final class Builder {

        private boolean countRejectedAsFailure;
        private final Map<Integer, Classification> overrides = new HashMap<>();

        private Builder() {
        }

        /** Whether 401/403 are recorded as breaker failures rather than ignored. */
        public Builder countRejectedAsFailure(boolean countRejectedAsFailure) {
            this.countRejectedAsFailure = countRejectedAsFailure;
            return this;
        }

        public Builder override(int status, Classification classification) {
            overrides.put(status, requireNonNull(classification, "classification"));
            return this;
        }

        public DefaultFailureClassifier build() {
            return new DefaultFailureClassifier(countRejectedAsFailure, overrides);
        }
    }
}

package io.pipecrafts.prototype.integration.client;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;

import io.pipecrafts.prototype.integration.client.ResilientClientProperties.Outcome;
import io.pipecrafts.prototype.integration.client.ResilientClientProperties.Policy;
import io.pipecrafts.prototype.integration.failure.BreakerOutcome;
import io.pipecrafts.prototype.integration.failure.Classification;
import io.pipecrafts.prototype.integration.failure.DefaultFailureClassifier;
import io.pipecrafts.prototype.integration.failure.FailureClassifier;
import io.pipecrafts.prototype.integration.failure.FailureReason;

/**
 * Picks the {@link FailureClassifier} for a client: a bean qualified with the client name, else the YAML
 * {@code policy}, else the default taxonomy. One instance per client, so the breaker and the status handler always
 * share it.
 *
 * <p>Qualified classifier beans must be pure functions of the status and must not depend on HTTP clients, or the
 * client being instrumented would depend on itself.
 */
public class ClientPolicyResolver {

    private final ResilientClientProperties properties;
    private final ListableBeanFactory beanFactory;
    private final Map<String, FailureClassifier> resolved = new ConcurrentHashMap<>();

    ClientPolicyResolver(ResilientClientProperties properties, ListableBeanFactory beanFactory) {
        this.properties = properties;
        this.beanFactory = beanFactory;
    }

    FailureClassifier resolve(String clientName) {
        return resolved.computeIfAbsent(clientName, this::build);
    }

    /** Names of {@link FailureClassifier} beans qualified for the client. */
    List<String> qualifiedBeanNames(String clientName) {
        return Arrays.stream(beanFactory.getBeanNamesForType(FailureClassifier.class))
                .filter(beanName -> BeanFactoryAnnotationUtils.isQualifierMatch(clientName::equals, beanName,
                        beanFactory))
                .toList();
    }

    private FailureClassifier build(String clientName) {
        List<String> qualified = qualifiedBeanNames(clientName);
        if (!qualified.isEmpty()) {
            return beanFactory.getBean(qualified.getFirst(), FailureClassifier.class);
        }
        ResilientClientProperties.Client client = properties.clients().get(clientName);
        if (client == null || !client.policy().isCustom()) {
            return DefaultFailureClassifier.standard();
        }
        return fromPolicy(client.policy());
    }

    static FailureClassifier fromPolicy(Policy policy) {
        DefaultFailureClassifier.Builder builder = DefaultFailureClassifier.builder()
                .countRejectedAsFailure(policy.countRejectedAsFailure());
        policy.statusOverrides().forEach((status, outcome) -> builder.override(status,
                classification(status, outcome, policy.countRejectedAsFailure())));
        return builder.build();
    }

    static Classification classification(int status, Outcome outcome, boolean countRejectedAsFailure) {
        return switch (outcome) {
            case SUCCESS -> Classification.success();
            case BUSINESS -> Classification.business();
            case DEPENDENCY_FAILURE -> Classification.dependencyFailure(switch (status) {
                case 408 -> FailureReason.TIMEOUT;
                case 429 -> FailureReason.THROTTLED;
                default -> FailureReason.SERVER_ERROR;
            });
            case DEPENDENCY_REJECTED -> Classification
                    .dependencyRejected(countRejectedAsFailure ? BreakerOutcome.FAILURE : BreakerOutcome.IGNORED);
        };
    }
}

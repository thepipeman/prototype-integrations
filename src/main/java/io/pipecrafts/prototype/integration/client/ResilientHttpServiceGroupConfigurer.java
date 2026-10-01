package io.pipecrafts.prototype.integration.client;

import static org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils.isQualifierMatch;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.boot.http.client.autoconfigure.HttpClientProperties;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientProperties;
import org.springframework.core.Ordered;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;
import org.springframework.web.service.invoker.HttpServiceProxyFactory;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.pipecrafts.prototype.integration.failure.DefaultFailureClassifier;
import io.pipecrafts.prototype.integration.failure.DownstreamFailureLog;
import io.pipecrafts.prototype.integration.failure.DownstreamStatusHandler;
import io.pipecrafts.prototype.integration.failure.ErrorBodyParser;
import io.pipecrafts.prototype.integration.failure.FailureClassifier;

/**
 * Applies status handling, translation, logging and the circuit breaker to every HTTP service group.
 *
 * <p>Ordered last so it runs after Boot's properties and {@code RestClientCustomizer} configurers; that is what
 * lets it guarantee the breaker is the innermost interceptor. Misconfiguration fails at startup rather than
 * silently falling back to defaults.
 */
public final class ResilientHttpServiceGroupConfigurer implements RestClientHttpServiceGroupConfigurer {

    private static final Logger log = LoggerFactory.getLogger(ResilientHttpServiceGroupConfigurer.class);

    private final ListableBeanFactory beanFactory;
    private final CircuitBreakerRegistry breakerRegistry;
    private final ResilientClientsProperties resilientProperties;
    private final HttpServiceClientProperties serviceClientProperties;
    private final ErrorBodyParser errorBodyParser;
    private final DownstreamFailureLog failureLog;

    public ResilientHttpServiceGroupConfigurer(ListableBeanFactory beanFactory, CircuitBreakerRegistry breakerRegistry,
            ResilientClientsProperties resilientProperties, HttpServiceClientProperties serviceClientProperties,
            ErrorBodyParser errorBodyParser, DownstreamFailureLog failureLog) {
        this.beanFactory = beanFactory;
        this.breakerRegistry = breakerRegistry;
        this.resilientProperties = resilientProperties;
        this.serviceClientProperties = serviceClientProperties;
        this.errorBodyParser = errorBodyParser;
        this.failureLog = failureLog;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void configureGroups(Groups<RestClient.Builder> groups) {
        Set<String> names = new TreeSet<>();
        groups.forEachGroup((group, clientBuilder, factoryBuilder) -> names.add(group.name()));

        validateTransportAndProperties(names);
        Map<String, FailureClassifier> classifiers = qualifiedClassifiers(names);
        Map<String, List<ResilientClientCustomizer>> customizers = qualifiedCustomizers(names);
        warnOnOrphanBreakerInstances(names);

        groups.forEachGroup((group, clientBuilder, factoryBuilder) -> configure(group.name(), clientBuilder,
                factoryBuilder, classifiers.getOrDefault(group.name(), DefaultFailureClassifier.standard()),
                customizers.getOrDefault(group.name(), List.of())));
    }

    private void configure(String name, RestClient.Builder clientBuilder, HttpServiceProxyFactory.Builder factoryBuilder,
            FailureClassifier classifier, List<ResilientClientCustomizer> customizers) {
        // Matches every status so Spring's default 4xx/5xx handler is never reached.
        clientBuilder.defaultStatusHandler(status -> true, new DownstreamStatusHandler(name, classifier, errorBodyParser));

        CircuitBreakerInterceptor breaker = null;
        if (resilientProperties.breakerEnabled(name)) {
            breaker = new CircuitBreakerInterceptor(name, breakerRegistry, classifier);
            clientBuilder.requestInterceptor(breaker);
            // Created eagerly so actuator shows every client from startup, not only after its first call.
            breakerRegistry.circuitBreaker(name);
        }

        customizers.forEach(customizer -> customizer.customize(clientBuilder));

        if (breaker != null) {
            CircuitBreakerInterceptor innermost = breaker;
            clientBuilder.requestInterceptors(interceptors -> {
                interceptors.remove(innermost);
                interceptors.add(innermost);
            });
        }

        factoryBuilder.httpRequestValuesProcessor(new OperationAttributeProcessor());
        factoryBuilder.exchangeAdapterDecorator(adapter -> new DownstreamExchangeAdapter(adapter, name, failureLog));
    }

    private void validateTransportAndProperties(Set<String> names) {
        for (String name : names) {
            HttpClientProperties transport = serviceClientProperties.get(name);
            if (transport == null || !StringUtils.hasText(transport.getBaseUrl())) {
                throw new IllegalStateException(
                        "HTTP service group '%s' has no spring.http.serviceclient.%s.base-url".formatted(name, name));
            }
        }
        for (String configured : resilientProperties.clients().keySet()) {
            if (!names.contains(configured)) {
                throw new IllegalStateException(
                        "prototype.clients.%s does not match any registered HTTP service group %s"
                                .formatted(configured, names));
            }
        }
    }

    private Map<String, FailureClassifier> qualifiedClassifiers(Set<String> names) {
        Map<String, FailureClassifier> byGroup = new HashMap<>();
        beanFactory.getBeansOfType(FailureClassifier.class).forEach((beanName, classifier) -> {
            for (String group : groupsFor(FailureClassifier.class, beanName, names)) {
                if (byGroup.putIfAbsent(group, classifier) != null) {
                    throw new IllegalStateException(
                            "More than one FailureClassifier bean is qualified for HTTP service group '%s'"
                                    .formatted(group));
                }
            }
        });
        return byGroup;
    }

    private Map<String, List<ResilientClientCustomizer>> qualifiedCustomizers(Set<String> names) {
        Map<ResilientClientCustomizer, String> beanNames = new IdentityHashMap<>();
        beanFactory.getBeansOfType(ResilientClientCustomizer.class)
                .forEach((beanName, customizer) -> beanNames.put(customizer, beanName));

        Map<String, List<ResilientClientCustomizer>> byGroup = new LinkedHashMap<>();
        beanFactory.getBeanProvider(ResilientClientCustomizer.class).orderedStream().forEach(customizer -> {
            for (String group : groupsFor(ResilientClientCustomizer.class, beanNames.get(customizer), names)) {
                byGroup.computeIfAbsent(group, key -> new ArrayList<>()).add(customizer);
            }
        });
        return byGroup;
    }

    private List<String> groupsFor(Class<?> type, @Nullable String beanName, Set<String> names) {
        if (beanName == null) {
            throw new IllegalStateException("Cannot resolve the bean name of a " + type.getSimpleName());
        }
        List<String> matched = names.stream()
                .filter(group -> isQualifierMatch(group::equals, beanName, beanFactory))
                .toList();
        if (matched.isEmpty()) {
            throw new IllegalStateException(
                    "%s bean '%s' must be @Qualifier-ed with a registered HTTP service group name %s"
                            .formatted(type.getSimpleName(), beanName, names));
        }
        return matched;
    }

    private void warnOnOrphanBreakerInstances(Set<String> names) {
        breakerRegistry.getAllCircuitBreakers().stream()
                .map(CircuitBreaker::getName)
                .filter(name -> !names.contains(name))
                .forEach(name -> log.warn(
                        "resilience4j.circuitbreaker.instances.{} does not match any registered HTTP service group {}",
                        name, names));
    }
}

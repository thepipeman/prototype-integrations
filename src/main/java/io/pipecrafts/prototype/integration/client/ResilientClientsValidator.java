package io.pipecrafts.prototype.integration.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.jspecify.annotations.Nullable;
import org.springframework.beans.factory.ListableBeanFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.util.ClassUtils;
import org.springframework.web.client.RestClient;

/**
 * Fails startup, naming the client and the fix, when the YAML cannot be honoured, so a typo never leaves a
 * downstream silently unprotected.
 */
public class ResilientClientsValidator implements SmartInitializingSingleton {

    private final ResilientClientProperties properties;
    private final ListableBeanFactory beanFactory;
    private final ClientPolicyResolver policies;
    private final ObjectProvider<ResilientClientGroupConfigurer> groupConfigurer;

    ResilientClientsValidator(ResilientClientProperties properties, ListableBeanFactory beanFactory,
            ClientPolicyResolver policies, ObjectProvider<ResilientClientGroupConfigurer> groupConfigurer) {
        this.properties = properties;
        this.beanFactory = beanFactory;
        this.policies = policies;
        this.groupConfigurer = groupConfigurer;
    }

    @Override
    public void afterSingletonsInstantiated() {
        Map<String, String> clientByBean = new HashMap<>();
        new TreeMap<>(properties.clients()).forEach((name, client) -> {
            if (!client.enabled()) {
                return;
            }
            if (client.bean() != null && client.httpServiceGroup() != null) {
                throw fail(name, "set either bean or http-service-group, not both");
            }
            if (client.httpServiceGroup() != null) {
                validateGroup(name, client.httpServiceGroup());
            }
            else {
                String beanName = client.beanName(name);
                String other = clientByBean.putIfAbsent(beanName, name);
                if (other != null) {
                    throw new IllegalStateException(
                            "resilient-client.clients.%s: '%s' and '%s' both target bean '%s'. One RestClient bean per downstream."
                                    .formatted(name, other, name, beanName));
                }
                validateBean(name, beanName);
            }
            List<String> qualified = policies.qualifiedBeanNames(name);
            if (!qualified.isEmpty() && client.policy().isCustom()) {
                throw fail(name, "policy defined twice. Use the bean or YAML, not both.");
            }
        });
    }

    private void validateBean(String name, String beanName) {
        List<String> restClients = List.of(beanFactory.getBeanNamesForType(RestClient.class, true, false));
        if (restClients.contains(beanName)) {
            return;
        }
        @Nullable Class<?> type = beanFactory.containsBean(beanName) ? beanFactory.getType(beanName, false) : null;
        if (type == null) {
            throw new IllegalStateException("resilient-client.clients.%s.bean '%s' not found. RestClient beans: %s"
                    .formatted(name, beanName, restClients));
        }
        throw fail(name, "bean '%s' is a %s, not a RestClient. Point 'bean' at the RestClient it's built from."
                .formatted(beanName, ClassUtils.getShortName(type)));
    }

    private void validateGroup(String name, String groupName) {
        ResilientClientGroupConfigurer configurer = groupConfigurer.getIfAvailable();
        if (configurer == null || !configurer.instrumented(groupName)) {
            throw fail(name, "http-service-group '%s' not found among the registered RestClient HTTP service groups"
                    .formatted(groupName));
        }
    }

    private static IllegalStateException fail(String name, String message) {
        return new IllegalStateException("resilient-client.clients.%s: %s".formatted(name, message));
    }
}

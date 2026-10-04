package io.pipecrafts.prototype.integration.client;

import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.web.client.RestClient;

/**
 * Rebuilds each {@link RestClient} bean listed as a {@code bean} target with its resilience settings applied.
 *
 * <p>Registered as a static bean and resolving its collaborators lazily, so the registries it needs are not created
 * before the other post-processors exist. Only {@code RestClient} beans named in the YAML are touched.
 */
public class ResilientClientInstrumenter implements BeanPostProcessor {

    private static final Logger log = LoggerFactory.getLogger(ResilientClientInstrumenter.class);

    private final ObjectProvider<ResilientClientProperties> properties;
    private final ObjectProvider<ResilientClients> resilientClients;

    ResilientClientInstrumenter(ObjectProvider<ResilientClientProperties> properties,
            ObjectProvider<ResilientClients> resilientClients) {
        this.properties = properties;
        this.resilientClients = resilientClients;
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof RestClient restClient)) {
            return bean;
        }
        ResilientClientProperties config = properties.getObject();
        String clientName = targets(config).get(beanName);
        if (clientName == null) {
            return bean;
        }
        ResilientClientProperties.Client client = config.clients().get(clientName);
        RestClient instrumented = restClient.mutate().apply(resilientClients.getObject().forClient(clientName))
                .build();
        log.info("resilient-client '{}' -> bean '{}' [breaker={}, error-handling={}]", clientName, beanName,
                client.circuitBreaker().enabled() ? "on" : "off", client.errorHandling().enabled() ? "on" : "off");
        return instrumented;
    }

    /** Bean name to client name for every enabled client that targets a bean. */
    static Map<String, String> targets(ResilientClientProperties properties) {
        Map<String, String> targets = new HashMap<>();
        if (!properties.enabled()) {
            return targets;
        }
        properties.clients().forEach((name, client) -> {
            if (client.enabled() && client.httpServiceGroup() == null) {
                targets.putIfAbsent(client.beanName(name), name);
            }
        });
        return targets;
    }
}

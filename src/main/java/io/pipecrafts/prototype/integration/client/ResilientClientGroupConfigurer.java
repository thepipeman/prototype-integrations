package io.pipecrafts.prototype.integration.client;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.core.Ordered;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.support.RestClientHttpServiceGroupConfigurer;

/**
 * Applies a listed client's settings to the builder of the HTTP service group it targets. Runs last so the group's
 * own interceptors stay outermost.
 */
public class ResilientClientGroupConfigurer implements RestClientHttpServiceGroupConfigurer {

    private final ResilientClientProperties properties;
    private final ResilientClients resilientClients;
    private final Set<String> instrumentedGroups = ConcurrentHashMap.newKeySet();

    ResilientClientGroupConfigurer(ResilientClientProperties properties, ResilientClients resilientClients) {
        this.properties = properties;
        this.resilientClients = resilientClients;
    }

    @Override
    public void configureGroups(Groups<RestClient.Builder> groups) {
        Map<String, String> clientByGroup = new HashMap<>();
        properties.clients().forEach((name, client) -> {
            if (client.enabled() && client.httpServiceGroup() != null) {
                clientByGroup.putIfAbsent(client.httpServiceGroup(), name);
            }
        });
        groups.filter(group -> clientByGroup.containsKey(group.name()))
                .forEachClient((group, builder) -> {
                    builder.apply(resilientClients.forClient(clientByGroup.get(group.name())));
                    instrumentedGroups.add(group.name());
                });
    }

    boolean instrumented(String groupName) {
        return instrumentedGroups.contains(groupName);
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }
}

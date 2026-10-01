package io.pipecrafts.prototype.integration.demo.profile;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.pipecrafts.prototype.integration.client.ResilientRestClients;
import io.pipecrafts.prototype.integration.demo.DemoClients;

@Component
public class ProfileClient {

    private final RestClient restClient;

    public ProfileClient(ResilientRestClients clients) {
        this.restClient = clients.create(DemoClients.PROFILE);
    }

    public Profile getProfile(String id) {
        return restClient.get().uri("/profiles/{id}", id).retrieve().body(Profile.class);
    }

    public Profile findByMobile(String mobile) {
        return restClient.get().uri("/profiles?mobile={mobile}", mobile).retrieve().body(Profile.class);
    }
}

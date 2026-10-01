package io.pipecrafts.prototype.integration.demo.profile;

import java.util.Optional;

import org.springframework.stereotype.Service;

import io.pipecrafts.prototype.integration.call.Downstream;

@Service
public class ProfileFacade {

    private final ProfileClient client;

    public ProfileFacade(ProfileClient client) {
        this.client = client;
    }

    /** A missing profile is a normal outcome here, not an error. */
    public Optional<Profile> find(String id) {
        return Downstream.call(() -> Optional.of(client.getProfile(id)))
                .onBusiness(404, Optional::empty)
                .get();
    }

    public Profile findByMobile(String mobile) {
        return client.findByMobile(mobile);
    }
}

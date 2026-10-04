package io.pipecrafts.prototype.integration.demo.profile;

import org.springframework.stereotype.Component;

import io.pipecrafts.prototype.integration.demo.profile.generated.api.ProfileApi;
import io.pipecrafts.prototype.integration.demo.profile.generated.model.ProfileDto;

/** Adapts the generated {@link ProfileApi} to the demo's own {@link Profile} type. */
@Component
public class ProfileClient {

    private final ProfileApi api;

    public ProfileClient(ProfileApi api) {
        this.api = api;
    }

    public Profile getProfile(String id) {
        return toProfile(api.getProfile(id));
    }

    public Profile findByMobile(String mobile) {
        return toProfile(api.findProfileByMobile(mobile));
    }

    private static Profile toProfile(ProfileDto dto) {
        return new Profile(dto.getId(), dto.getName(), dto.getMobile());
    }
}

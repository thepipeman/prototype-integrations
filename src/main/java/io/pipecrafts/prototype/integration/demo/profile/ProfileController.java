package io.pipecrafts.prototype.integration.demo.profile;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.pipecrafts.prototype.integration.web.ApiErrorCode;
import io.pipecrafts.prototype.integration.web.ApiException;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/demo/profiles")
public class ProfileController {

    private final ProfileFacade facade;

    public ProfileController(ProfileFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/{id}")
    public Profile find(@PathVariable @Size(max = 64) String id) {
        return facade.find(id).orElseThrow(() -> new ApiException(ApiErrorCode.PROFILE_NOT_FOUND));
    }

    @GetMapping
    public Profile findByMobile(@RequestParam @NotBlank @Size(max = 20) String mobile) {
        return facade.findByMobile(mobile);
    }
}

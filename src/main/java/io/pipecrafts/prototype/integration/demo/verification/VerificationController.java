package io.pipecrafts.prototype.integration.demo.verification;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.pipecrafts.prototype.integration.demo.verification.VerificationOutcome.CodeExpired;
import io.pipecrafts.prototype.integration.demo.verification.VerificationOutcome.CodeIncorrect;
import io.pipecrafts.prototype.integration.demo.verification.VerificationOutcome.Verified;
import io.pipecrafts.prototype.integration.web.ApiErrorCode;
import io.pipecrafts.prototype.integration.web.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/demo/verifications")
public class VerificationController {

    private final VerificationFacade facade;

    public VerificationController(VerificationFacade facade) {
        this.facade = facade;
    }

    @PostMapping
    public Challenge start(@Valid @RequestBody StartChallengeRequest request) {
        return facade.startChallenge(request);
    }

    @PostMapping("/{challengeId}/verify")
    public VerificationResult verify(@PathVariable @Size(max = 64) String challengeId,
            @Valid @RequestBody VerifyRequest request) {
        return switch (facade.verify(challengeId, request.code())) {
            case Verified(VerificationResult result) -> result;
            case CodeIncorrect() -> throw new ApiException(ApiErrorCode.VERIFICATION_CODE_INCORRECT);
            case CodeExpired() -> throw new ApiException(ApiErrorCode.VERIFICATION_CODE_EXPIRED);
        };
    }
}

package io.pipecrafts.prototype.integration.demo.verification;

import org.springframework.stereotype.Service;

import io.pipecrafts.prototype.integration.call.Downstream;
import io.pipecrafts.prototype.integration.demo.verification.VerificationOutcome.CodeExpired;
import io.pipecrafts.prototype.integration.demo.verification.VerificationOutcome.CodeIncorrect;

@Service
public class VerificationFacade {

    private final VerificationClient client;

    public VerificationFacade(VerificationClient client) {
        this.client = client;
    }

    public Challenge startChallenge(StartChallengeRequest request) {
        return client.startChallenge(request);
    }

    /**
     * A wrong or expired code is an expected outcome. Any other rejection propagates as a business error, and
     * outages, timeouts and credential faults propagate as dependency failures.
     */
    public VerificationOutcome verify(String challengeId, String code) {
        return Downstream.call(() -> VerificationOutcome.verified(client.verify(challengeId, new VerifyRequest(code))))
                .onBusinessCode("CODE_INCORRECT", CodeIncorrect::new)
                .onBusinessCode("CODE_EXPIRED", CodeExpired::new)
                .get();
    }
}

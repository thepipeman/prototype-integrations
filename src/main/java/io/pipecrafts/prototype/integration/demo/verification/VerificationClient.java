package io.pipecrafts.prototype.integration.demo.verification;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

@HttpExchange("/challenges")
public interface VerificationClient {

    @PostExchange
    Challenge startChallenge(@RequestBody StartChallengeRequest request);

    @PostExchange("/{challengeId}/verify")
    VerificationResult verify(@PathVariable String challengeId, @RequestBody VerifyRequest request);
}

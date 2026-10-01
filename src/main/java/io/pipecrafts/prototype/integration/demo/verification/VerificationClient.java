package io.pipecrafts.prototype.integration.demo.verification;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.pipecrafts.prototype.integration.client.ResilientRestClients;
import io.pipecrafts.prototype.integration.demo.DemoClients;

@Component
public class VerificationClient {

    private final RestClient restClient;

    public VerificationClient(ResilientRestClients clients) {
        this.restClient = clients.create(DemoClients.VERIFICATION);
    }

    public Challenge startChallenge(StartChallengeRequest request) {
        return restClient.post().uri("/challenges").body(request).retrieve().body(Challenge.class);
    }

    public VerificationResult verify(String challengeId, VerifyRequest request) {
        return restClient.post().uri("/challenges/{challengeId}/verify", challengeId).body(request).retrieve()
                .body(VerificationResult.class);
    }
}

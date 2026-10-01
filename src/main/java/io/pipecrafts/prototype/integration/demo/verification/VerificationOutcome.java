package io.pipecrafts.prototype.integration.demo.verification;

/** Every expected result of checking a code; anything else is an error that propagates. */
public sealed interface VerificationOutcome {

    static VerificationOutcome verified(VerificationResult result) {
        return new Verified(result);
    }

    record Verified(VerificationResult result) implements VerificationOutcome {
    }

    record CodeIncorrect() implements VerificationOutcome {
    }

    record CodeExpired() implements VerificationOutcome {
    }
}

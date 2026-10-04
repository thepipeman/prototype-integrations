package io.pipecrafts.prototype.integration.demo;

/**
 * Downstream names. Each is the key under {@code demo.clients} and {@code resilient-client.clients}, the
 * circuit breaker name and the Resilience4j instance name.
 */
public final class DemoClients {

    public static final String PROFILE = "profile-service";
    public static final String VERIFICATION = "verification-service";
    public static final String ACCOUNT = "account-service";
    public static final String NOTIFICATION = "notification-service";

    private DemoClients() {
    }
}

package io.pipecrafts.prototype.integration.demo;

/**
 * Downstream names. Each is the property key under {@code demo.clients} and {@code resilient-client.circuit-breaker},
 * the Resilience4j instance name, and the bean name of the downstream's {@code RestClient}.
 */
public final class DemoClients {

    public static final String PROFILE = "profile-service";
    public static final String VERIFICATION = "verification-service";
    public static final String ACCOUNT = "account-service";
    public static final String NOTIFICATION = "notification-service";

    private DemoClients() {
    }
}

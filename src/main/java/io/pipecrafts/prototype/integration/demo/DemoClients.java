package io.pipecrafts.prototype.integration.demo;

/**
 * Downstream names. Each is the property key under {@code spring.http.serviceclient} and {@code prototype.clients}
 * and the Resilience4j instance name.
 */
public final class DemoClients {

    public static final String PROFILE = "profile-service";
    public static final String VERIFICATION = "verification-service";
    public static final String ACCOUNT = "account-service";
    public static final String NOTIFICATION = "notification-service";

    private DemoClients() {
    }
}

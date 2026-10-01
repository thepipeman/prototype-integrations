package io.pipecrafts.prototype.integration.demo;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.service.registry.ImportHttpServices;

import io.pipecrafts.prototype.integration.demo.account.AccountClient;
import io.pipecrafts.prototype.integration.demo.notification.NotificationClient;
import io.pipecrafts.prototype.integration.demo.profile.ProfileClient;
import io.pipecrafts.prototype.integration.demo.verification.VerificationClient;

/**
 * One line per downstream. The group name is also the property key under {@code spring.http.serviceclient}, the
 * {@code prototype.clients} key, the Resilience4j instance name and the {@code @Qualifier} for per-client beans.
 */
@Configuration(proxyBeanMethods = false)
@ImportHttpServices(group = DemoClients.PROFILE, types = ProfileClient.class)
@ImportHttpServices(group = DemoClients.VERIFICATION, types = VerificationClient.class)
@ImportHttpServices(group = DemoClients.ACCOUNT, types = AccountClient.class)
@ImportHttpServices(group = DemoClients.NOTIFICATION, types = NotificationClient.class)
public class DemoClients {

    public static final String PROFILE = "profile-service";
    public static final String VERIFICATION = "verification-service";
    public static final String ACCOUNT = "account-service";
    public static final String NOTIFICATION = "notification-service";
}

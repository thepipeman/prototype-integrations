package io.pipecrafts.prototype.integration.demo.profile;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/profiles")
public interface ProfileClient {

    @GetExchange("/{id}")
    Profile getProfile(@PathVariable String id);

    @GetExchange
    Profile findByMobile(@RequestParam String mobile);
}

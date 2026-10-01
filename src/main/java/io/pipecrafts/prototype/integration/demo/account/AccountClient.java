package io.pipecrafts.prototype.integration.demo.account;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

@HttpExchange("/accounts/{id}")
public interface AccountClient {

    @GetExchange("/balance")
    Balance getBalance(@PathVariable String id);

    @GetExchange("/transactions")
    Transactions getTransactions(@PathVariable String id);
}

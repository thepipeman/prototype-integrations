package io.pipecrafts.prototype.integration.demo.account;

import org.springframework.stereotype.Service;

@Service
public class AccountFacade {

    private final AccountClient client;

    public AccountFacade(AccountClient client) {
        this.client = client;
    }

    public Balance balance(String accountId) {
        return client.getBalance(accountId);
    }

    public Transactions transactions(String accountId) {
        return client.getTransactions(accountId);
    }
}

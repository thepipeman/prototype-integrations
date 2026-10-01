package io.pipecrafts.prototype.integration.demo.account;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.pipecrafts.prototype.integration.client.ResilientRestClients;
import io.pipecrafts.prototype.integration.demo.DemoClients;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(ResilientRestClients clients) {
        this.restClient = clients.create(DemoClients.ACCOUNT);
    }

    public Balance getBalance(String id) {
        return restClient.get().uri("/accounts/{id}/balance", id).retrieve().body(Balance.class);
    }

    public Transactions getTransactions(String id) {
        return restClient.get().uri("/accounts/{id}/transactions", id).retrieve().body(Transactions.class);
    }
}

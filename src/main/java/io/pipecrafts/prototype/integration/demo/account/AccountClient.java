package io.pipecrafts.prototype.integration.demo.account;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import io.pipecrafts.prototype.integration.demo.DemoClients;

@Component
public class AccountClient {

    private final RestClient restClient;

    public AccountClient(@Qualifier(DemoClients.ACCOUNT) RestClient restClient) {
        this.restClient = restClient;
    }

    public Balance getBalance(String id) {
        return restClient.get().uri("/accounts/{id}/balance", id).retrieve().body(Balance.class);
    }

    public Transactions getTransactions(String id) {
        return restClient.get().uri("/accounts/{id}/transactions", id).retrieve().body(Transactions.class);
    }
}

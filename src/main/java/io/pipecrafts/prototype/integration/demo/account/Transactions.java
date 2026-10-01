package io.pipecrafts.prototype.integration.demo.account;

import java.math.BigDecimal;
import java.util.List;

public record Transactions(String accountId, List<Transaction> items) {

    public record Transaction(String id, BigDecimal amount, String description) {
    }
}

package io.pipecrafts.prototype.integration.demo.account;

import java.math.BigDecimal;

public record Balance(String accountId, BigDecimal amount, String currency) {
}

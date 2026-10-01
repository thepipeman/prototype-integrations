package io.pipecrafts.prototype.integration.demo.account;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/demo/accounts/{accountId}")
public class AccountController {

    private final AccountFacade facade;

    public AccountController(AccountFacade facade) {
        this.facade = facade;
    }

    @GetMapping("/balance")
    public Balance balance(@PathVariable @Size(max = 64) String accountId) {
        return facade.balance(accountId);
    }

    @GetMapping("/transactions")
    public Transactions transactions(@PathVariable @Size(max = 64) String accountId) {
        return facade.transactions(accountId);
    }
}

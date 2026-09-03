package com.example.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class WalletController {

    @Autowired
    private WalletService walletService;

    @PostMapping("/balance")
    public Map<String, Object> getBalance(@RequestBody Map<String, Object> req) {
        return walletService.getBalance(req);
    }

    @PostMapping("/deposit")
    public Map<String, Object> deposit(@RequestBody Map<String, Object> req) {
        return walletService.deposit(req);
    }

    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody Map<String, Object> req) {
        return walletService.withdraw(req);
    }

    @PostMapping("/transfer")
    public Map<String, Object> transfer(@RequestBody Map<String, Object> req) {
        return walletService.transfer(req);
    }

    @PostMapping("/create-account")
    public Map<String, Object> createAccount(@RequestBody Map<String, Object> req) {
        return walletService.createAccount(req);
    }
}

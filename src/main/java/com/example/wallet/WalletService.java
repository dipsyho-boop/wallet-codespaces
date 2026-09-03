package com.example.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class WalletService {

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private MaxAccountIdRepository maxAccountIdRepository;

    // -------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------

    // 生成 26 位 Transaction ID (17位時間 yyyyMMddHHmmssSSS + 3位隨機數 + 6位 Account_ID)
    private String generateTransactionId(String accountId) {
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");
        String timeStr = now.format(formatter);
        int randomNum = ThreadLocalRandom.current().nextInt(100, 1000);
        return timeStr + randomNum + accountId;
    }

    // 格式化 6 位 Account ID (不足 6 位前補 0)
    private String formatAccountId(String accountIdStr) {
        if (accountIdStr == null) return null;
        try {
            long id = Long.parseLong(accountIdStr.trim());
            return String.format("%06d", id);
        } catch (Exception e) {
            return accountIdStr;
        }
    }

    // 驗證 Amount 參數 (必須為數字、不可為負數、小數點最多 2 位)
    private BigDecimal validateAndParseAmount(Object dollarsObj) {
        if (dollarsObj == null) return null;
        try {
            BigDecimal amount = new BigDecimal(dollarsObj.toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) return null; // 必須大於 0
            if (amount.scale() > 2) return null;                     // 小數點最多兩位
            return amount;
        } catch (Exception e) {
            return null;
        }
    }

    // 統一回傳格式 Map
    private Map<String, Object> response(String result) {
        Map<String, Object> res = new HashMap<>();
        res.put("result", result);
        return res;
    }

    // -------------------------------------------------------------
    // API 1: 查 Balance with Cache
    // -------------------------------------------------------------
    @Transactional(readOnly = true)
    // 確保即使傳入 "1" 或 "000001"，Redis 內存取的 Key 都是統一格式化後的 Key
    @Cacheable(value = "balance", key = "T(String).format('%06d', T(Long).parseLong(#req.get('Account_ID')))")
    public Map<String, Object> getBalance(Map<String, Object> req) {
        String accountId = formatAccountId((String) req.get("Account_ID"));
        if (accountId == null || !transactionRepository.existsByAccountId(accountId)) {
            return response("Account not found");
        }
        BigDecimal sum = transactionRepository.sumActionByAccountId(accountId);
        return response(sum == null ? "0.00" : sum.setScale(2, BigDecimal.ROUND_HALF_UP).toString());
    }

    // -------------------------------------------------------------
    // API 2: 入錢 (Deposit)
    // -------------------------------------------------------------
    @Transactional
    @CacheEvict(value = "balance", key = "T(String).format('%06d', T(Long).parseLong(#req.get('Account_ID')))")
    public Map<String, Object> deposit(Map<String, Object> req) {
        BigDecimal amount = validateAndParseAmount(req.get("amount"));
        if (amount == null) return response("failed");

        String accountId = formatAccountId((String) req.get("Account_ID"));
        if (accountId == null || !transactionRepository.existsByAccountId(accountId)) {
            return response("Account not found");
        }

        // 悲觀鎖鎖定該 Account_ID 紀錄
        transactionRepository.findByAccountIdWithLock(accountId);

        try {
            Transaction tx = new Transaction();
            tx.setTransactionId(generateTransactionId(accountId));
            tx.setAccountId(accountId);
            tx.setAction(amount); // 正數
            tx.setCreateDate(LocalDateTime.now());
            transactionRepository.save(tx);
            return response("success");
        } catch (Exception e) {
            e.printStackTrace();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return response("failed");
        }
    }

    // -------------------------------------------------------------
    // API 3: 扣錢 (Withdraw)
    // -------------------------------------------------------------
    @Transactional
    @CacheEvict(value = "balance", key = "T(String).format('%06d', T(Long).parseLong(#req.get('Account_ID')))")
    public Map<String, Object> withdraw(Map<String, Object> req) {
        BigDecimal amount = validateAndParseAmount(req.get("amount"));
        if (amount == null) return response("failed");

        String accountId = formatAccountId((String) req.get("Account_ID"));
        if (accountId == null || !transactionRepository.existsByAccountId(accountId)) {
            return response("Account not found");
        }

        // 悲觀鎖鎖定該 Account_ID 紀錄
        transactionRepository.findByAccountIdWithLock(accountId);

        // 檢查餘額是否足夠扣數
        BigDecimal currentBalance = transactionRepository.sumActionByAccountId(accountId);
        if (currentBalance == null) currentBalance = BigDecimal.ZERO;

        if (currentBalance.compareTo(amount) < 0) {
            return response("Insufficient funds");
        }

        try {
            Transaction tx = new Transaction();
            tx.setTransactionId(generateTransactionId(accountId));
            tx.setAccountId(accountId);
            tx.setAction(amount.negate()); // 負數
            tx.setCreateDate(LocalDateTime.now());
            transactionRepository.save(tx);
            return response("success");
        } catch (Exception e) {
            e.printStackTrace();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return response("failed");
        }
    }

    // -------------------------------------------------------------
    // API 4: 過數 (Transfer) - 一筆交易，兩個帳號共用同一個 sharedTxId
    // -------------------------------------------------------------
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "balance", key = "T(String).format('%06d', T(Long).parseLong(#req.get('from_Account_ID')))" ),
        @CacheEvict(value = "balance", key = "T(String).format('%06d', T(Long).parseLong(#req.get('to_Account_ID')))" )
    })
    public Map<String, Object> transfer(Map<String, Object> req) {
        BigDecimal amount = validateAndParseAmount(req.get("amount"));
        if (amount == null) return response("failed");

        String fromAcc = formatAccountId((String) req.get("from_Account_ID"));
        String toAcc = formatAccountId((String) req.get("to_Account_ID"));

        if (fromAcc != null && fromAcc.equals(toAcc)) {
            return response("failed");
        }

        if (fromAcc == null || toAcc == null ||
            !transactionRepository.existsByAccountId(fromAcc) || 
            !transactionRepository.existsByAccountId(toAcc)) {
            return response("Account not found");
        }

        // 按字典順序悲觀鎖定兩者，防止死鎖 (Deadlock)
        if (fromAcc.compareTo(toAcc) < 0) {
            transactionRepository.findByAccountIdWithLock(fromAcc);
            transactionRepository.findByAccountIdWithLock(toAcc);
        } else {
            transactionRepository.findByAccountIdWithLock(toAcc);
            transactionRepository.findByAccountIdWithLock(fromAcc);
        }

        // 檢查餘額
        BigDecimal fromBalance = transactionRepository.sumActionByAccountId(fromAcc);
        if (fromBalance == null) fromBalance = BigDecimal.ZERO;

        if (fromBalance.compareTo(amount) < 0) {
            return response("Insufficient funds");
        }

        // 生成兩邊共用嘅 Transaction ID (26位)
        String sharedTxId = generateTransactionId(fromAcc);
        LocalDateTime now = LocalDateTime.now();

        try {
            // 1. From Account 扣數 (負數)
            transactionRepository.insertRecord(sharedTxId, fromAcc, amount.negate(), now);

            // 2. To Account 加數 (正數) - 共用相同 sharedTxId (複合主鍵)
            transactionRepository.insertRecord(sharedTxId, toAcc, amount, now);

            return response("success");
        } catch (Exception e) {
            e.printStackTrace();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return response("failed");
        }
    }

    // -------------------------------------------------------------
    // API 5: 開戶 (Create Account)
    // -------------------------------------------------------------
    @Transactional
    public Map<String, Object> createAccount(Map<String, Object> req) {
        BigDecimal amount = validateAndParseAmount(req.get("amount"));
        if (amount == null) return response("failed");

        // 悲觀鎖鎖定 max_account_id 表
        Optional<MaxAccountId> maxOpt = maxAccountIdRepository.findWithLock();

        String newAccountId;
        MaxAccountId maxEntity;

        if (maxOpt.isEmpty()) {
            newAccountId = "000001";
            maxEntity = new MaxAccountId();
            maxEntity.setId(1L);
            maxEntity.setMax(1L);
        } else {
            maxEntity = maxOpt.get();
            long nextVal = maxEntity.getMax() + 1;
            newAccountId = String.format("%06d", nextVal);
            maxEntity.setMax(nextVal);
        }

        try {
            Transaction tx = new Transaction();
            tx.setTransactionId(generateTransactionId(newAccountId));
            tx.setAccountId(newAccountId);
            tx.setAction(amount);
            tx.setCreateDate(LocalDateTime.now());
            transactionRepository.save(tx);

            maxAccountIdRepository.save(maxEntity);

            return response(newAccountId);
        } catch (Exception e) {
            e.printStackTrace();
            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            return response("failed");
        }
    }
}

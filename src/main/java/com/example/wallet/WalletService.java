package com.example.wallet;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    private String generateTransactionId(String accountId) {
        LocalDateTime now = LocalDateTime.now();
        // yyyyMMddHHmmss (14位) + SSSSS border/padding 到 6位 = 20位時間字串
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSSSSS");
        String timeStr = now.format(formatter);

        // 防護措施：確保 timeStr 至少有 20 位（如果 JVM 格式化出來不足 20 位自動補齊）
        if (timeStr.length() < 20) {
            timeStr = String.format("%-20s", timeStr).replace(' ', '0');
        } else if (timeStr.length() > 20) {
            timeStr = timeStr.substring(0, 20);
    }

    // 當 OS / JVM 精度不足，微秒尾數為 000 時
    if (timeStr.endsWith("000")) {
        // 生成 3 位隨機數 (100 - 999)
        int randomNum = ThreadLocalRandom.current().nextInt(100, 1000);
        // 安全截取前 17 位 + 3 位隨機數 = 剛好 20 位
        timeStr = timeStr.substring(0, 17) + randomNum;
    }

    // 確保 accountId 是標準 6 位格式
    String formattedAccountId = formatAccountId(accountId);

    // 組合：20 位時間 (固定) + 6 位 Account ID = 剛好 26 位
    return timeStr + formattedAccountId;
}


    private String formatAccountId(String accountIdStr) {
        if (accountIdStr == null) return null;
        try {
            long id = Long.parseLong(accountIdStr.trim());
            return String.format("%06d", id);
        } catch (Exception e) {
            return accountIdStr;
        }
    }

    private BigDecimal validateAndParseAmount(Object dollarsObj) {
        if (dollarsObj == null) return null;
        try {
            BigDecimal amount = new BigDecimal(dollarsObj.toString());
            if (amount.compareTo(BigDecimal.ZERO) <= 0) return null;
            if (amount.scale() > 2) return null;
            return amount;
        } catch (Exception e) {
            return null;
        }
    }

    private Map<String, Object> response(String result) {
        Map<String, Object> res = new HashMap<>();
        res.put("result", result);
        return res;
    }

    // -------------------------------------------------------------
    // API 1: 查 Balance
    // -------------------------------------------------------------
    @Transactional(readOnly = true)
    @Cacheable(
        value = "balance", 
        key = "T(String).format('%06d', T(Long).parseLong(#req.get('Account_ID')))",
        condition = "#req.get('Account_ID') != null"
    )
    public Map<String, Object> getBalance(Map<String, Object> req) {
        String accountId = formatAccountId((String) req.get("Account_ID"));
        if (accountId == null || !transactionRepository.existsByAccountId(accountId)) {
            return response("Account not found");
        }
        BigDecimal sum = transactionRepository.sumActionByAccountId(accountId);
        return response(sum == null ? "0.00" : sum.setScale(2, RoundingMode.HALF_UP).toString());
    }

    // -------------------------------------------------------------
    // API 2: 入錢 (Deposit)
    // -------------------------------------------------------------
    @Transactional
    @CacheEvict(
        value = "balance", 
        key = "T(String).format('%06d', T(Long).parseLong(#req.get('Account_ID')))",
        condition = "#req.get('Account_ID') != null"
    )
    public Map<String, Object> deposit(Map<String, Object> req) {
        // 統一 Key 名稱為 Account_ID / Amount (或統一小寫)
        BigDecimal amount = validateAndParseAmount(req.get("Amount"));
        if (amount == null) {
            throw new IllegalArgumentException("Invalid amount! Must be greater than 0.");
        }

        String accountId = formatAccountId((String) req.get("Account_ID"));
        if (accountId == null || !transactionRepository.existsByAccountId(accountId)) {
            // 帳號不存在時拋出 Exception，防止無效 Evict Cache
            throw new IllegalArgumentException("Account not found");
        }

        transactionRepository.findByAccountIdWithLock(accountId);

        try {
            Transaction tx = new Transaction();
            tx.setTransactionId(generateTransactionId(accountId));
            tx.setAccountId(accountId);
            tx.setAction(amount);
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
    @CacheEvict(
        value = "balance", 
        key = "T(String).format('%06d', T(Long).parseLong(#req.get('Account_ID')))",
        condition = "#req.get('Account_ID') != null"
    )
    public Map<String, Object> withdraw(Map<String, Object> req) {
        BigDecimal amount = validateAndParseAmount(req.get("Amount"));
        if (amount == null) {
            throw new IllegalArgumentException("Invalid amount! Must be greater than 0.");
        }

        String accountId = formatAccountId((String) req.get("Account_ID"));
        if (accountId == null || !transactionRepository.existsByAccountId(accountId)) {
            throw new IllegalArgumentException("Account not found");
        }

        transactionRepository.findByAccountIdWithLock(accountId);

        BigDecimal currentBalance = transactionRepository.sumActionByAccountId(accountId);
        if (currentBalance == null) currentBalance = BigDecimal.ZERO;

        if (currentBalance.compareTo(amount) < 0) {
            // 🌟 關鍵改動：餘額不足時拋出 Exception，確保 Cache 完好無損！
            throw new IllegalStateException("Insufficient funds");
        }

        try {
            Transaction tx = new Transaction();
            tx.setTransactionId(generateTransactionId(accountId));
            tx.setAccountId(accountId);
            tx.setAction(amount.negate());
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
    // API 4: 過數 (Transfer)
    // -------------------------------------------------------------
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "balance", key = "T(String).format('%06d', T(Long).parseLong(#req.get('from_Account_ID')))", condition = "#req.get('from_Account_ID') != null"),
        @CacheEvict(value = "balance", key = "T(String).format('%06d', T(Long).parseLong(#req.get('to_Account_ID')))", condition = "#req.get('to_Account_ID') != null")
    })
    public Map<String, Object> transfer(Map<String, Object> req) {
        BigDecimal amount = validateAndParseAmount(req.get("Amount"));
        if (amount == null) {
            throw new IllegalArgumentException("Invalid amount! Must be greater than 0.");
        }

        String fromAcc = formatAccountId((String) req.get("from_Account_ID"));
        String toAcc = formatAccountId((String) req.get("to_Account_ID"));

        if (fromAcc != null && fromAcc.equals(toAcc)) {
            throw new IllegalArgumentException("Cannot transfer to the same account");
        }

        if (fromAcc == null || toAcc == null ||
            !transactionRepository.existsByAccountId(fromAcc) || 
            !transactionRepository.existsByAccountId(toAcc)) {
            throw new IllegalArgumentException("Account not found");
        }

        // 字典順序鎖定防止死鎖 (Deadlock)
        if (fromAcc.compareTo(toAcc) < 0) {
            transactionRepository.findByAccountIdWithLock(fromAcc);
            transactionRepository.findByAccountIdWithLock(toAcc);
        } else {
            transactionRepository.findByAccountIdWithLock(toAcc);
            transactionRepository.findByAccountIdWithLock(fromAcc);
        }

        BigDecimal fromBalance = transactionRepository.sumActionByAccountId(fromAcc);
        if (fromBalance == null) fromBalance = BigDecimal.ZERO;

        if (fromBalance.compareTo(amount) < 0) {
            // 🌟 餘額不足拋出 Exception，維護 Cache 一致性
            throw new IllegalStateException("Insufficient funds");
        }

        String sharedTxId = generateTransactionId(fromAcc);
        LocalDateTime now = LocalDateTime.now();

        try {
            transactionRepository.insertRecord(sharedTxId, fromAcc, amount.negate(), now);
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
        BigDecimal amount = validateAndParseAmount(req.get("Amount"));
        if (amount == null) {
            throw new IllegalArgumentException("Invalid amount! Must be greater than 0.");
        }

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

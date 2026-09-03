package com.example.wallet;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, String> {
    
    boolean existsByAccountId(String accountId);

    // 悲觀鎖 SELECT ... FOR UPDATE：鎖定特定 Account_ID 所有的交易紀錄
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.accountId = :accountId")
    List<Transaction> findByAccountIdWithLock(@Param("accountId") String accountId);

    // 計算指定 Account_ID 的總餘額 (Sum of action)
    @Query("SELECT SUM(t.action) FROM Transaction t WHERE t.accountId = :accountId")
    BigDecimal sumActionByAccountId(@Param("accountId") String accountId);

    // 強制執行原生的 SQL INSERT，繞過 JPA save() 的覆蓋機制
    @Modifying
    @Query(value = "INSERT INTO transaction (transaction_id, account_id, action, create_date) VALUES (:txId, :accountId, :action, :createDate)", nativeQuery = true)
    void insertRecord(@Param("txId") String txId, @Param("accountId") String accountId, @Param("action") BigDecimal action, @Param("createDate") LocalDateTime createDate);
}

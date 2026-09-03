package com.example.wallet;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "transaction")
@IdClass(TransactionId.class)
public class Transaction {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Id
    @Column(name = "account_id")
    private String accountId;

    @Column(name = "action")
    private BigDecimal action;

    @Column(name = "create_date")
    private LocalDateTime createDate;

    // Getters and Setters
    public String getTransactionId() { return transactionId; }
    public void setTransactionId(String transactionId) { this.transactionId = transactionId; }

    public String getAccountId() { return accountId; }
    public void setAccountId(String accountId) { this.accountId = accountId; }

    public BigDecimal getAction() { return action; }
    public void setAction(BigDecimal action) { this.action = action; }

    public LocalDateTime getCreateDate() { return createDate; }
    public void setCreateDate(LocalDateTime createDate) { this.createDate = createDate; }
}

package com.example.wallet;

import java.io.Serializable;
import java.util.Objects;

public class TransactionId implements Serializable {
    private String transactionId;
    private String accountId;

    public TransactionId() {}

    public TransactionId(String transactionId, String accountId) {
        this.transactionId = transactionId;
        this.accountId = accountId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransactionId that = (TransactionId) o;
        return Objects.equals(transactionId, that.transactionId) && Objects.equals(accountId, that.accountId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(transactionId, accountId);
    }
}

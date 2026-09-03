package com.example.wallet;

import jakarta.persistence.*;

@Entity
@Table(name = "max_account_id")
public class MaxAccountId {

    @Id
    @Column(name = "id")
    private Long id = 1L; // 固定單一行記錄

    @Column(name = "max", nullable = false)
    private Long max;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getMax() { return max; }
    public void setMax(Long max) { this.max = max; }
}

package com.example.wallet;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MaxAccountIdRepository extends JpaRepository<MaxAccountId, Long> {

    // 悲觀鎖 SELECT ... FOR UPDATE 鎖定 max_account_id 表
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT m FROM MaxAccountId m WHERE m.id = 1")
    Optional<MaxAccountId> findWithLock();
}

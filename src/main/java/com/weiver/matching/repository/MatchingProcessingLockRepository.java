package com.weiver.matching.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

@Repository
@RequiredArgsConstructor
public class MatchingProcessingLockRepository {

    // WEIVER 매칭 교체·알림 집계가 공유하는 DB 전역 잠금 키.
    private static final long LOCK_KEY = 0x574549564552L;

    private final EntityManager entityManager;

    public boolean tryLock() {
        requireTransaction();
        return (Boolean) entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", LOCK_KEY)
                .getSingleResult();
    }

    public void lock() {
        requireTransaction();
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", LOCK_KEY)
                .getSingleResult();
    }

    private void requireTransaction() {
        Assert.state(TransactionSynchronizationManager.isActualTransactionActive(),
                "매칭 처리 잠금은 트랜잭션 안에서 획득해야 합니다.");
    }
}

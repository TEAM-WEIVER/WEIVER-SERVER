package com.weiver.matching.repository;

import jakarta.persistence.EntityManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.Assert;

@Repository
public class MatchingProcessingLockRepository {

    // 매칭 교체·알림 집계 및 향후 MatchResult 쓰기 경로도 반드시 이 동일 키의 잠금에 참여해야 한다.
    private static final long LOCK_KEY = 0x574549564552L;

    private final EntityManager entityManager;
    private final long lockTimeoutMillis;

    public MatchingProcessingLockRepository(EntityManager entityManager,
            @Value("${weiver.matching.lock-timeout-ms:5000}") long lockTimeoutMillis) {
        Assert.isTrue(lockTimeoutMillis > 0, "매칭 잠금 대기 제한은 0보다 커야 합니다.");
        this.entityManager = entityManager;
        this.lockTimeoutMillis = lockTimeoutMillis;
    }

    public boolean tryLock() {
        requireTransaction();
        return (Boolean) entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", LOCK_KEY)
                .getSingleResult();
    }

    public void lock() {
        requireTransaction();
        String previousTimeout = (String) entityManager.createNativeQuery("SELECT current_setting('lock_timeout')")
                .getSingleResult();
        setLocalLockTimeout(lockTimeoutMillis + "ms");
        entityManager.createNativeQuery("SELECT 1 FROM pg_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", LOCK_KEY)
                .getSingleResult();
        // 성공하면 기존 설정을 복원한다. 실패하면 트랜잭션 롤백이 SET LOCAL 설정을 되돌린다.
        setLocalLockTimeout(previousTimeout);
    }

    private void setLocalLockTimeout(String timeout) {
        entityManager.createNativeQuery("SELECT set_config('lock_timeout', :timeout, true)")
                .setParameter("timeout", timeout)
                .getSingleResult();
    }

    private void requireTransaction() {
        Assert.state(TransactionSynchronizationManager.isActualTransactionActive(),
                "매칭 처리 잠금은 트랜잭션 안에서 획득해야 합니다.");
    }
}

package com.weiver.auth.repository;

import com.weiver.global.common.UserRole;
import com.weiver.global.config.RedisConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.redis.DataRedisTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis 7에서 로그인 시도 제한 스크립트(tryAcquire)를 검증한다. Docker 데몬이 필요하다.
 */
@Testcontainers
@DataRedisTest
@Import({RedisConfig.class, LoginAttemptRepository.class})
class LoginAttemptRepositoryTest {

    private static final int MAX_ATTEMPTS = 5;
    private static final Duration WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK = Duration.ofMinutes(15);

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private LoginAttemptRepository repository;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    void flush() {
        redisTemplate.execute(connection -> {
            connection.serverCommands().flushDb();
            return null;
        }, true);
    }

    @Test
    @DisplayName("1~5번째 시도는 허용하고 6번째에서 차단 키(약 15분)를 만들며 시도 카운터를 지운다")
    void tryAcquire_allowsFiveThenBlocksSixth() {
        // given
        String attemptKey = "login:attempts:applicant:user@test.com";
        String blockedKey = "login:blocked:applicant:user@test.com";

        // when & then
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            assertThat(repository.tryAcquire("user@test.com", UserRole.APPLICANT, MAX_ATTEMPTS, WINDOW, BLOCK))
                    .as("%d번째 시도", attempt)
                    .isTrue();
        }
        assertThat(redisTemplate.opsForValue().get(attemptKey)).isEqualTo("5");
        assertThat(redisTemplate.getExpire(attemptKey, TimeUnit.MILLISECONDS))
                .isBetween(Duration.ofMinutes(14).toMillis(), WINDOW.toMillis());

        assertThat(repository.tryAcquire("user@test.com", UserRole.APPLICANT, MAX_ATTEMPTS, WINDOW, BLOCK)).isFalse();
        assertThat(redisTemplate.hasKey(attemptKey)).isFalse();
        assertThat(redisTemplate.getExpire(blockedKey, TimeUnit.MILLISECONDS))
                .isBetween(Duration.ofMinutes(14).toMillis(), BLOCK.toMillis());
    }

    @Test
    @DisplayName("차단 중에는 스크립트 첫 단계(차단 키 확인)에서 거절해 차단 TTL이 늘지 않고 시도 카운터도 다시 만들지 않는다")
    void tryAcquire_afterBlock_doesNotExtendBlockTtl() {
        // given
        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 1; attempt++) {
            repository.tryAcquire("weiver", UserRole.COMPANY, MAX_ATTEMPTS, WINDOW, BLOCK);
        }
        String blockedKey = "login:blocked:company:weiver";
        Long blockedPttl = redisTemplate.getExpire(blockedKey, TimeUnit.MILLISECONDS);

        // when: 더 긴 차단 기간을 넘겨도
        boolean allowed = repository.tryAcquire("weiver", UserRole.COMPANY, MAX_ATTEMPTS, WINDOW, Duration.ofHours(2));

        // then
        assertThat(allowed).isFalse();
        assertThat(redisTemplate.getExpire(blockedKey, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(blockedPttl);
        assertThat(redisTemplate.hasKey("login:attempts:company:weiver")).isFalse();
    }

    @Test
    @DisplayName("시도 카운터 TTL은 첫 시도에만 걸리고 이후 시도로 늘지 않는다")
    void tryAcquire_windowTtlNotExtended() {
        // given
        String attemptKey = "login:attempts:applicant:user@test.com";
        repository.tryAcquire("user@test.com", UserRole.APPLICANT, MAX_ATTEMPTS, Duration.ofMinutes(1), BLOCK);
        Long firstPttl = redisTemplate.getExpire(attemptKey, TimeUnit.MILLISECONDS);

        // when
        repository.tryAcquire("user@test.com", UserRole.APPLICANT, MAX_ATTEMPTS, Duration.ofHours(1), BLOCK);

        // then
        assertThat(firstPttl).isBetween(1L, Duration.ofMinutes(1).toMillis());
        assertThat(redisTemplate.getExpire(attemptKey, TimeUnit.MILLISECONDS))
                .isPositive()
                .isLessThanOrEqualTo(firstPttl);
    }

    @Test
    @DisplayName("TTL 없는(PTTL -1) 기존 시도 카운터 키는 다음 tryAcquire 때 TTL이 붙는다")
    void tryAcquire_recoversAttemptKeyWithoutTtl() {
        // given
        String attemptKey = "login:attempts:applicant:legacy@test.com";
        redisTemplate.opsForValue().set(attemptKey, "2");
        assertThat(redisTemplate.getExpire(attemptKey, TimeUnit.MILLISECONDS)).isEqualTo(-1L);

        // when
        boolean allowed = repository.tryAcquire("legacy@test.com", UserRole.APPLICANT, MAX_ATTEMPTS, WINDOW, BLOCK);

        // then
        assertThat(allowed).isTrue();
        assertThat(redisTemplate.opsForValue().get(attemptKey)).isEqualTo("3");
        assertThat(redisTemplate.getExpire(attemptKey, TimeUnit.MILLISECONDS))
                .isBetween(Duration.ofMinutes(14).toMillis(), WINDOW.toMillis());
    }

    @Test
    @DisplayName("구직자와 기업은 같은 식별자라도 네임스페이스가 분리된다")
    void tryAcquire_separatesNamespaceByRole() {
        // given: 구직자 쪽을 차단
        for (int attempt = 1; attempt <= MAX_ATTEMPTS + 1; attempt++) {
            repository.tryAcquire("same-id", UserRole.APPLICANT, MAX_ATTEMPTS, WINDOW, BLOCK);
        }

        // when & then
        assertThat(repository.tryAcquire("same-id", UserRole.APPLICANT, MAX_ATTEMPTS, WINDOW, BLOCK)).isFalse();
        assertThat(repository.tryAcquire("same-id", UserRole.COMPANY, MAX_ATTEMPTS, WINDOW, BLOCK)).isTrue();
        assertThat(redisTemplate.hasKey("login:blocked:company:same-id")).isFalse();
    }

    @Test
    @DisplayName("clearAttempts는 해당 역할의 시도 카운터만 지운다")
    void clearAttempts_deletesOnlyRoleAttemptKey() {
        // given
        repository.tryAcquire("same-id", UserRole.APPLICANT, MAX_ATTEMPTS, WINDOW, BLOCK);
        repository.tryAcquire("same-id", UserRole.COMPANY, MAX_ATTEMPTS, WINDOW, BLOCK);

        // when
        repository.clearAttempts("same-id", UserRole.APPLICANT);

        // then
        assertThat(redisTemplate.hasKey("login:attempts:applicant:same-id")).isFalse();
        assertThat(redisTemplate.hasKey("login:attempts:company:same-id")).isTrue();
    }
}

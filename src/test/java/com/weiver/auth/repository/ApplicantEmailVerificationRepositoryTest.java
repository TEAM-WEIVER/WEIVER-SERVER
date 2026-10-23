package com.weiver.auth.repository;

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
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis 7에서 INCREMENT_WITH_TTL_ON_FIRST 스크립트와 시도 카운터 동작을 검증한다. Docker 데몬이 필요하다.
 */
@Testcontainers
@DataRedisTest
@Import({RedisConfig.class, ApplicantEmailVerificationRepository.class})
class ApplicantEmailVerificationRepositoryTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private ApplicantEmailVerificationRepository repository;

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
    @DisplayName("증가 스크립트는 Long을 반환하고 첫 증가에만 TTL을 건다 (두 번째 증가는 더 긴 TTL을 넘겨도 PTTL이 늘지 않는다)")
    void incrementScript_setsTtlOnlyOnFirstIncrement() {
        // given
        String key = "test:counter";

        // when
        Object first = redisTemplate.execute(RedisCounterScripts.INCREMENT_WITH_TTL_ON_FIRST, List.of(key), "60000");
        Long firstPttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        Object second = redisTemplate.execute(RedisCounterScripts.INCREMENT_WITH_TTL_ON_FIRST, List.of(key), "1800000");
        Long secondPttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        // then
        assertThat(first).isInstanceOf(Long.class).isEqualTo(1L);
        assertThat(second).isInstanceOf(Long.class).isEqualTo(2L);
        assertThat(firstPttl).isBetween(1L, 60000L);
        assertThat(secondPttl).isPositive().isLessThanOrEqualTo(firstPttl);
    }

    @Test
    @DisplayName("TTL 없는(PTTL -1) 기존 카운터 키는 다음 증가 때 TTL이 붙는다")
    void incrementScript_recoversKeyWithoutTtl() {
        // given
        String key = "test:legacy";
        redisTemplate.opsForValue().set(key, "3");
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isEqualTo(-1L);

        // when
        Long count = redisTemplate.execute(RedisCounterScripts.INCREMENT_WITH_TTL_ON_FIRST, List.of(key), "60000");

        // then
        assertThat(count).isEqualTo(4L);
        assertThat(redisTemplate.getExpire(key, TimeUnit.MILLISECONDS)).isBetween(1L, 60000L);
    }

    @Test
    @DisplayName("시도 카운터는 applicant:email:attempts 키에 30분 TTL로 쌓이고 재증가로 TTL이 늘지 않는다")
    void incrementAttemptCount_usesAttemptKeyAndKeepsTtl() {
        // given
        String email = "user@test.com";
        String key = "applicant:email:attempts:" + email;

        // when
        long first = repository.incrementAttemptCount(email, Duration.ofMinutes(30));
        Long firstPttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        long second = repository.incrementAttemptCount(email, Duration.ofHours(2));
        Long secondPttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        // then
        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(firstPttl).isBetween(Duration.ofMinutes(29).toMillis(), Duration.ofMinutes(30).toMillis());
        assertThat(secondPttl).isPositive().isLessThanOrEqualTo(firstPttl);
    }
}

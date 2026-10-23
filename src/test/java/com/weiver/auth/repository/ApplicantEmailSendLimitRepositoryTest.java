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
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 Redis 7에서 발송 횟수 카운터의 키와 TTL을 검증한다. Docker 데몬이 필요하다.
 */
@Testcontainers
@DataRedisTest
@Import({RedisConfig.class, ApplicantEmailSendLimitRepository.class})
class ApplicantEmailSendLimitRepositoryTest {

    @Container
    @ServiceConnection(name = "redis")
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    @Autowired
    private ApplicantEmailSendLimitRepository repository;

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
    @DisplayName("1분 카운터는 분 단위 키에 1분 TTL로 쌓이고 재증가로 TTL이 늘지 않는다")
    void incrementMinuteCount() {
        // given
        String key = "applicant:email:send:minute:user@test.com";

        // when
        long first = repository.incrementMinuteCount("user@test.com", Duration.ofMinutes(1));
        Long firstPttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        long second = repository.incrementMinuteCount("user@test.com", Duration.ofHours(1));
        Long secondPttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        // then
        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(firstPttl).isBetween(Duration.ofSeconds(50).toMillis(), Duration.ofMinutes(1).toMillis());
        assertThat(secondPttl).isPositive().isLessThanOrEqualTo(firstPttl);
    }

    @Test
    @DisplayName("1시간 카운터는 시간 단위 키에 1시간 TTL로 쌓이고 1분 카운터와 분리된다")
    void incrementHourCount() {
        // given
        String key = "applicant:email:send:hour:user@test.com";

        // when
        long first = repository.incrementHourCount("user@test.com", Duration.ofHours(1));
        Long firstPttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);
        long second = repository.incrementHourCount("user@test.com", Duration.ofHours(2));
        Long secondPttl = redisTemplate.getExpire(key, TimeUnit.MILLISECONDS);

        // then
        assertThat(first).isEqualTo(1L);
        assertThat(second).isEqualTo(2L);
        assertThat(firstPttl).isBetween(Duration.ofMinutes(59).toMillis(), Duration.ofHours(1).toMillis());
        assertThat(secondPttl).isPositive().isLessThanOrEqualTo(firstPttl);
        assertThat(redisTemplate.hasKey("applicant:email:send:minute:user@test.com")).isFalse();
    }
}

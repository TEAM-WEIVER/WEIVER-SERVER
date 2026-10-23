package com.weiver.auth.repository;

import com.weiver.global.common.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;

/**
 * 실제 Redis로 만들 수 없는 경우(스크립트 결과 null)를 Mockito로 검증한다.
 * 정상 동작은 LoginAttemptRepositoryTest(Testcontainers Redis)가 검증한다.
 */
@ExtendWith(MockitoExtension.class)
class LoginAttemptRepositoryFailClosedTest {

    @InjectMocks
    private LoginAttemptRepository repository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Test
    @SuppressWarnings("unchecked")
    @DisplayName("스크립트 결과가 null이면 시도를 거절한다 (fail-closed)")
    void tryAcquire_nullResult_returnsFalse() {
        // given
        given(redisTemplate.execute(any(RedisScript.class), anyList(), any(), any(), any())).willReturn(null);

        // when
        boolean allowed = repository.tryAcquire("user@test.com", UserRole.APPLICANT, 5,
                Duration.ofMinutes(15), Duration.ofMinutes(15));

        // then
        assertThat(allowed).isFalse();
    }
}

package com.weiver.global.security.jwt.repository;

import com.weiver.global.common.UserRole;
import com.weiver.global.security.jwt.type.RefreshTokenRotationResult;
import com.weiver.global.security.util.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.ValueOperations;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class RefreshTokenRepositoryTest {

    @InjectMocks
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RedisOperations<String, String> redisOperations;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final Long USER_ID = 1L;
    private static final UserRole USER_ROLE = UserRole.APPLICANT;
    private static final String KEY = "refresh:applicant:1";
    private static final String OLD_TOKEN = "old_refresh_token";
    private static final String NEW_TOKEN = "new_refresh_token";
    private static final long TTL_MILLIS = 1000L * 60 * 60 * 24 * 7;

    @BeforeEach
    void setUp() {
        when(redisTemplate.execute(any(SessionCallback.class))).thenAnswer(invocation -> {
            SessionCallback<RefreshTokenRotationResult> callback = invocation.getArgument(0);
            return callback.execute(redisOperations);
        });
        when(redisOperations.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("Redis에 저장된 토큰이 없으면 NOT_FOUND 반환")
    public void rotateIfMatches_notFound() {
        // given
        when(valueOperations.get(KEY)).thenReturn(null);

        // when
        RefreshTokenRotationResult result = refreshTokenRepository.rotateIfMatches(
                USER_ID, USER_ROLE, OLD_TOKEN, NEW_TOKEN, TTL_MILLIS);

        // then
        assertThat(result).isEqualTo(RefreshTokenRotationResult.NOT_FOUND);
    }

    @Test
    @DisplayName("저장된 해시와 입력 토큰 해시가 다르면 MISMATCH 반환")
    public void rotateIfMatches_mismatch() {
        // given
        when(valueOperations.get(KEY)).thenReturn("different_hash_value");

        // when
        RefreshTokenRotationResult result = refreshTokenRepository.rotateIfMatches(
                USER_ID, USER_ROLE, OLD_TOKEN, NEW_TOKEN, TTL_MILLIS);

        // then
        assertThat(result).isEqualTo(RefreshTokenRotationResult.MISMATCH);
    }

    @Test
    @DisplayName("해시 일치 후 WATCH 트랜잭션 성공 시 ROTATED 반환")
    public void rotateIfMatches_rotated() {
        // given
        when(valueOperations.get(KEY)).thenReturn(TokenHashUtil.sha256(OLD_TOKEN));
        when(redisOperations.exec()).thenReturn(List.of("OK"));

        // when
        RefreshTokenRotationResult result = refreshTokenRepository.rotateIfMatches(
                USER_ID, USER_ROLE, OLD_TOKEN, NEW_TOKEN, TTL_MILLIS);

        // then
        assertThat(result).isEqualTo(RefreshTokenRotationResult.ROTATED);
    }

    @Test
    @DisplayName("WATCH 충돌로 exec가 null 반환 시 CONCURRENT_MODIFIED 반환")
    public void rotateIfMatches_concurrentModified() {
        // given
        when(valueOperations.get(KEY)).thenReturn(TokenHashUtil.sha256(OLD_TOKEN));
        when(redisOperations.exec()).thenReturn(null);

        // when
        RefreshTokenRotationResult result = refreshTokenRepository.rotateIfMatches(
                USER_ID, USER_ROLE, OLD_TOKEN, NEW_TOKEN, TTL_MILLIS);

        // then
        assertThat(result).isEqualTo(RefreshTokenRotationResult.CONCURRENT_MODIFIED);
    }
}
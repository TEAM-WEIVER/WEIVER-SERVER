package com.weiver.global.security.jwt.repository;

import com.weiver.global.security.util.TokenHashUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class BlacklistTokenRepositoryTest {

    @InjectMocks
    private BlacklistTokenRepository blacklistTokenRepository;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private static final String ACCESS_TOKEN = "access_token";
    private static final String EXPECTED_KEY = "BLACKLIST:" + TokenHashUtil.sha256(ACCESS_TOKEN);
    private static final long TTL_MILLIS = 1000L * 60 * 10;

    @BeforeEach
    void setUp() {
        // 모든 테스트가 valueOperations를 stub하지는 않으므로 lenient 처리는 각 테스트에서.
    }

    @Test
    @DisplayName("save: 양수 ttl이면 해시된 키로 BLACKLIST 값을 TTL과 함께 저장")
    public void save_positiveTtl() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        // when
        blacklistTokenRepository.save(ACCESS_TOKEN, TTL_MILLIS);

        // then
        verify(valueOperations).set(EXPECTED_KEY, "logout", TTL_MILLIS, TimeUnit.MILLISECONDS);
    }

    @Test
    @DisplayName("save: ttl이 0이면 Redis에 저장하지 않음")
    public void save_zeroTtl() {
        // when
        blacklistTokenRepository.save(ACCESS_TOKEN, 0L);

        // then
        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("save: ttl이 음수이면 Redis에 저장하지 않음")
    public void save_negativeTtl() {
        // when
        blacklistTokenRepository.save(ACCESS_TOKEN, -1L);

        // then
        verify(redisTemplate, never()).opsForValue();
        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("exists: 해시된 키가 Redis에 존재하면 true 반환")
    public void exists_true() {
        // given
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(true);

        // when
        boolean result = blacklistTokenRepository.exists(ACCESS_TOKEN);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("exists: 해시된 키가 Redis에 없으면 false 반환")
    public void exists_false() {
        // given
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(false);

        // when
        boolean result = blacklistTokenRepository.exists(ACCESS_TOKEN);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("exists: Redis가 null을 반환해도 false (NPE 없이 안전)")
    public void exists_nullSafe() {
        // given
        when(redisTemplate.hasKey(EXPECTED_KEY)).thenReturn(null);

        // when
        boolean result = blacklistTokenRepository.exists(ACCESS_TOKEN);

        // then
        assertThat(result).isFalse();
    }
}
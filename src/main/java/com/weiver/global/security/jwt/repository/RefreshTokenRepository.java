package com.weiver.global.security.jwt.repository;

import com.weiver.global.security.util.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final RedisTemplate<String, String> redisTemplate;

    public void save(Long userId, String refreshToken, long ttlMillis) {
        redisTemplate.opsForValue().set(
                generateKey(userId),
                TokenHashUtil.sha256(refreshToken),
                ttlMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public Optional<String> findHashByUserId(Long userId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(generateKey(userId))
        );
    }

    public boolean matches(Long userId, String refreshToken) {
        return findHashByUserId(userId)
                .map(savedHash -> savedHash.equals(TokenHashUtil.sha256(refreshToken)))
                .orElse(false);
    }

    public void deleteByUserId(Long userId) {
        redisTemplate.delete(generateKey(userId));
    }

    public boolean existsByUserId(Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(generateKey(userId)));
    }

    private String generateKey(Long userId) {
        return REFRESH_TOKEN_PREFIX + userId;
    }
}

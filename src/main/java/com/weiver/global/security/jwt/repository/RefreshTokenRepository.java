package com.weiver.global.security.jwt.repository;

import com.weiver.global.common.UserRole;
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

    public void save(UserRole userRole, Long userId, String refreshToken, long ttlMillis) {
        redisTemplate.opsForValue().set(
                generateKey(userRole, userId),
                TokenHashUtil.sha256(refreshToken),
                ttlMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public Optional<String> findHashByUserId(UserRole userRole, Long userId) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(generateKey(userRole, userId))
        );
    }

    public boolean matches(UserRole userRole, Long userId, String refreshToken) {
        return findHashByUserId(userRole, userId)
                .map(savedHash -> savedHash.equals(TokenHashUtil.sha256(refreshToken)))
                .orElse(false);
    }

    public void deleteByUserId(UserRole userRole, Long userId) {
        redisTemplate.delete(generateKey(userRole, userId));
    }

    public boolean existsByUserId(UserRole userRole, Long userId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(generateKey(userRole, userId)));
    }

    private String generateKey(UserRole userRole, Long userId) {
        return REFRESH_TOKEN_PREFIX + userRole.name().toLowerCase() + ":" + userId;
    }
}

package com.weiver.global.security.jwt.repository;

import com.weiver.global.common.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TokenVersionRepository {

    private static final String TOKEN_VERSION_PREFIX = "token-version:";
    private static final long DEFAULT_VERSION = 0L;

    private final RedisTemplate<String, String> redisTemplate;

    public long getCurrentVersion(String publicId, UserRole userRole) {
        String value = redisTemplate.opsForValue().get(generateKey(publicId, userRole));

        if(value == null) return DEFAULT_VERSION;

        return Long.parseLong(value);
    }

    public void increaseVersion(String publicId, UserRole userRole) {
        Long version = redisTemplate.opsForValue().increment(generateKey(publicId, userRole));

        if(version == null) {
            throw new IllegalStateException("토큰 버전 증가에 실패했습니다.");
        }
    }

    private String generateKey(String publicId, UserRole userRole) {
        return TOKEN_VERSION_PREFIX + userRole.name() + ":" + publicId;
    }
}
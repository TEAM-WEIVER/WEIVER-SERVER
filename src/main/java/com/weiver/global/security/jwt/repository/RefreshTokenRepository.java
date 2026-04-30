package com.weiver.global.security.jwt.repository;

import com.weiver.global.common.UserRole;
import com.weiver.global.security.jwt.type.RefreshTokenRotationResult;
import com.weiver.global.security.util.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private static final String REFRESH_TOKEN_PREFIX = "refresh:";

    private final RedisTemplate<String, String> redisTemplate;

    public RefreshTokenRotationResult rotateIfMatches(
            Long userId,
            UserRole userRole,
            String oldRefreshToken,
            String newRefreshToken,
            long ttlMillis
    ) {
        String key = generateKey(userId, userRole);
        String oldHash = TokenHashUtil.sha256(oldRefreshToken);
        String newHash = TokenHashUtil.sha256(newRefreshToken);

        return redisTemplate.execute(new SessionCallback<>() {
            @Override
            public RefreshTokenRotationResult execute(RedisOperations operations) throws DataAccessException {
                operations.watch(key);

                String savedHash = (String) operations.opsForValue().get(key);

                if(savedHash == null) {
                    operations.unwatch();
                    return RefreshTokenRotationResult.NOT_FOUND;
                }

                if(!savedHash.equals(oldHash)) {
                    operations.unwatch();
                    return RefreshTokenRotationResult.MISMATCH;
                }

                operations.multi();
                operations.opsForValue().set(key, newHash, ttlMillis, TimeUnit.MILLISECONDS);

                List<Object> execResult = operations.exec();

                if(execResult == null || execResult.isEmpty()) {
                    return RefreshTokenRotationResult.CONCURRENT_MODIFIED;
                }

                return RefreshTokenRotationResult.ROTATED;
            }
        });
    }

    public void save(Long userId, UserRole userRole, String refreshToken, long ttlMillis) {
        redisTemplate.opsForValue().set(
                generateKey(userId, userRole),
                TokenHashUtil.sha256(refreshToken),
                ttlMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public Optional<String> findHashByUserId(Long userId, UserRole userRole) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(generateKey(userId, userRole))
        );
    }

    public boolean matches(Long userId, UserRole userRole, String refreshToken) {
        return findHashByUserId(userId, userRole)
                .map(savedHash -> savedHash.equals(TokenHashUtil.sha256(refreshToken)))
                .orElse(false);
    }

    public void deleteByUserId(Long userId, UserRole userRole) {
        redisTemplate.delete(generateKey(userId, userRole));
    }

    public boolean existsByUserId(Long userId, UserRole userRole) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(generateKey(userId, userRole)));
    }

    private String generateKey(Long userId, UserRole userRole) {
        return REFRESH_TOKEN_PREFIX + userRole.name().toLowerCase() + ":" + userId;
    }
}

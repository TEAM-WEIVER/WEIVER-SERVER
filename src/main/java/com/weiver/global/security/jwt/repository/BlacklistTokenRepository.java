package com.weiver.global.security.jwt.repository;

import com.weiver.global.security.util.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.concurrent.TimeUnit;

@Repository
@RequiredArgsConstructor
public class BlacklistTokenRepository {

    private static final String BLACKLIST_PREFIX = "BLACKLIST:";
    private static final String BLACKLIST_VALUE = "logout";

    private final RedisTemplate<String, String> redisTemplate;

    public void save(String accessToken, long ttlMillis) {
        if(ttlMillis <= 0) {
            return;
        }

        redisTemplate.opsForValue().set(
                generateKey(accessToken),
                BLACKLIST_VALUE,
                ttlMillis,
                TimeUnit.MILLISECONDS
        );
    }

    public boolean exists(String accessToken) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(generateKey(accessToken)));
    }

    private String generateKey(String accessToken) {
        return BLACKLIST_PREFIX + TokenHashUtil.sha256(accessToken);
    }
}

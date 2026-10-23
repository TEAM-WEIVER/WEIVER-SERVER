package com.weiver.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;

/**
 * 이메일 인증번호 발송 횟수 카운터. 회원가입 인증과 비밀번호 재설정이 같은 이메일 기준 카운터를 공유한다.
 */
@Repository
@RequiredArgsConstructor
public class ApplicantEmailSendLimitRepository {

    private static final String MINUTE_PREFIX = "applicant:email:send:minute:";
    private static final String HOUR_PREFIX = "applicant:email:send:hour:";

    private final RedisTemplate<String, String> redisTemplate;

    public long incrementMinuteCount(String email, Duration ttl) {
        return increment(generateMinuteKey(email), ttl);
    }

    public long incrementHourCount(String email, Duration ttl) {
        return increment(generateHourKey(email), ttl);
    }

    private long increment(String key, Duration ttl) {
        Long count = redisTemplate.execute(
                RedisCounterScripts.INCREMENT_WITH_TTL_ON_FIRST,
                List.of(key),
                String.valueOf(ttl.toMillis())
        );
        return RedisCounterScripts.toCount(count);
    }

    private String generateMinuteKey(String email) {
        return MINUTE_PREFIX + email;
    }

    private String generateHourKey(String email) {
        return HOUR_PREFIX + email;
    }
}

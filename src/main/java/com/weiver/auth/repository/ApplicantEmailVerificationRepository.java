package com.weiver.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ApplicantEmailVerificationRepository {

    private static final String CODE_PREFIX = "applicant:email:code:";
    private static final String VERIFIED_TOKEN_PREFIX = "applicant:email:verified:";
    private static final String ATTEMPT_PREFIX = "applicant:email:attempts:";

    private final RedisTemplate<String, String> redisTemplate;

    public void saveCode(String email, String code, Duration ttl) {
        redisTemplate.opsForValue().set(
                generateCodeKey(email),
                code,
                ttl
        );
    }

    public Optional<String> findCodeByEmail(String email) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(generateCodeKey(email))
        );
    }

    public void deleteCode(String email) {
        redisTemplate.delete(generateCodeKey(email));
    }

    public long incrementAttemptCount(String email, Duration ttl) {
        String key = generateAttemptKey(email);
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1L) {
            redisTemplate.expire(key, ttl);
        }
        return count == null ? 0L : count;
    }

    public void deleteAttemptCount(String email) {
        redisTemplate.delete(generateAttemptKey(email));
    }

    public void saveVerifiedToken(String verificationToken, String email, Duration ttl) {
        redisTemplate.opsForValue().set(
                generateVerifiedTokenKey(verificationToken),
                email,
                ttl
        );
    }

    public Optional<String> findAndDeleteVerifiedToken(String verifiedToken) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().getAndDelete(generateVerifiedTokenKey(verifiedToken))
        );
    }

    private String generateCodeKey(String email) {
        return CODE_PREFIX + email;
    }

    private String generateVerifiedTokenKey(String verifiedToken) {
        return VERIFIED_TOKEN_PREFIX + verifiedToken;
    }

    private String generateAttemptKey(String email) {
        return ATTEMPT_PREFIX + email;
    }
}

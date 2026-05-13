package com.weiver.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class ApplicantSignupTokenRepository {

    private static final String SIGNUP_TOKEN_PREFIX = "applicant:signup:token:";
    private final RedisTemplate<String, String> redisTemplate;

    public void save(String signupToken, String applicantPublicId, Duration ttl) {
        redisTemplate.opsForValue().set(
                generateKey(signupToken),
                applicantPublicId,
                ttl
        );
    }

    public Optional<String> findApplicantPublicId(String signupToken) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().get(generateKey(signupToken))
        );
    }

    public Optional<String> findAndDelete(String signupToken) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().getAndDelete(generateKey(signupToken))
        );
    }

    public void delete(String signupToken) {
        redisTemplate.delete(generateKey(signupToken));
    }

    private String generateKey(String signupToken) {
        return SIGNUP_TOKEN_PREFIX + signupToken;
    }
}

package com.weiver.auth.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

/**
 * 로그인 상태 비밀번호 변경 1단계(현재 비밀번호 재인증) 통과 시 발급하는 단기 재인증 토큰 저장소.
 * 2단계(새 비밀번호 설정)에서 findAndDelete로 원자적으로 소비하여 1단계 통과를 서버에서 강제한다.
 */
@Repository
@RequiredArgsConstructor
public class ApplicantPasswordReauthTokenRepository {

    private static final String REAUTH_TOKEN_PREFIX = "applicant:password:reauth:";
    private final RedisTemplate<String, String> redisTemplate;

    public void save(String reauthToken, String applicantPublicId, Duration ttl) {
        redisTemplate.opsForValue().set(
                generateKey(reauthToken),
                applicantPublicId,
                ttl
        );
    }

    public Optional<String> findAndDelete(String reauthToken) {
        return Optional.ofNullable(
                redisTemplate.opsForValue().getAndDelete(generateKey(reauthToken))
        );
    }

    private String generateKey(String reauthToken) {
        return REAUTH_TOKEN_PREFIX + reauthToken;
    }
}

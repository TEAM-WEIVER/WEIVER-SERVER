package com.weiver.auth.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.security.util.TokenHashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class KakaoOAuthTicketRepository {

    private static final String KEY_PREFIX = "applicant:kakao:";
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    public void save(String purpose, String binding, String token, Object value, Duration ttl) {
        try {
            redisTemplate.opsForValue().set(generateKey(purpose, binding, token), objectMapper.writeValueAsString(value), ttl);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카카오 인증 증표를 저장할 수 없습니다.");
        }
    }

    public <T> Optional<T> consume(String purpose, String binding, String token, Class<T> type) {
        String value = redisTemplate.opsForValue().getAndDelete(generateKey(purpose, binding, token));
        if (value == null) {
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(value, type));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("카카오 인증 증표를 읽을 수 없습니다.");
        }
    }

    private String generateKey(String purpose, String binding, String token) {
        return KEY_PREFIX + purpose + ":" + TokenHashUtil.sha256(binding) + ":" + TokenHashUtil.sha256(token);
    }
}

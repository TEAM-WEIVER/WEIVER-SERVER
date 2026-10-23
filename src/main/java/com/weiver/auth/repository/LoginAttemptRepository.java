package com.weiver.auth.repository;

import com.weiver.global.common.UserRole;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * 로그인 시도 횟수와 차단 상태. 식별자(구직자 email, 기업 loginId)와 역할별로 키 네임스페이스를 나눈다.
 */
@Repository
@RequiredArgsConstructor
public class LoginAttemptRepository {

    private static final String ATTEMPT_PREFIX = "login:attempts:";
    private static final String BLOCKED_PREFIX = "login:blocked:";
    private static final long ALLOWED = 1L;

    /**
     * 차단 확인과 시도 기록을 한 번에 처리한다. 동시 요청이 확인과 기록 사이 틈으로 한도를 넘지 못하게 한다.
     * 1) 차단 키(KEYS[2])가 있으면 0(차단)을 돌려준다.
     * 2) 시도 카운터(KEYS[1])를 INCR하고, 첫 증가이거나 TTL이 없을 때만 ARGV[1] 밀리초 TTL을 건다.
     * 3) 카운터가 ARGV[2]를 넘으면 차단 키를 ARGV[3] 밀리초 TTL로 NX 설정(기존 차단 TTL을 늘리지 않음)하고
     *    카운터를 지운 뒤 0(차단)을 돌려준다.
     * 4) 그 밖에는 1(허용)을 돌려준다.
     */
    private static final RedisScript<Long> TRY_ACQUIRE_SCRIPT = RedisScript.of("""
            if redis.call('EXISTS', KEYS[2]) == 1 then
                return 0
            end
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('PTTL', KEYS[1]) == -1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            if count > tonumber(ARGV[2]) then
                redis.call('SET', KEYS[2], '1', 'PX', ARGV[3], 'NX')
                redis.call('DEL', KEYS[1])
                return 0
            end
            return 1
            """, Long.class);

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 로그인 시도 하나를 기록하고 허용 여부를 돌려준다. 비밀번호 비교 전에 호출한다.
     * window 안에서 maxAttempts번까지 허용하고, 그다음 시도부터 blockDuration 동안 차단한다.
     *
     * @return 허용이면 true, 차단이면 false
     */
    public boolean tryAcquire(String identifier, UserRole role, int maxAttempts, Duration window, Duration blockDuration) {
        Long result = redisTemplate.execute(
                TRY_ACQUIRE_SCRIPT,
                List.of(generateAttemptKey(identifier, role), generateBlockedKey(identifier, role)),
                String.valueOf(window.toMillis()),
                String.valueOf(maxAttempts),
                String.valueOf(blockDuration.toMillis())
        );
        return result != null && result == ALLOWED;
    }

    public void clearAttempts(String identifier, UserRole role) {
        redisTemplate.delete(generateAttemptKey(identifier, role));
    }

    private String generateAttemptKey(String identifier, UserRole role) {
        return ATTEMPT_PREFIX + role.name().toLowerCase(Locale.ROOT) + ":" + identifier;
    }

    private String generateBlockedKey(String identifier, UserRole role) {
        return BLOCKED_PREFIX + role.name().toLowerCase(Locale.ROOT) + ":" + identifier;
    }
}

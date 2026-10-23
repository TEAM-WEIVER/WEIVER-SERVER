package com.weiver.auth.repository;

import org.springframework.data.redis.core.script.RedisScript;

/**
 * auth 저장소들이 함께 쓰는 Redis 카운터 Lua 스크립트.
 * INCR와 TTL 설정을 한 번에 실행해, 증가와 만료 사이에 끊겨 TTL 없는 카운터가 남는 일을 막는다.
 */
final class RedisCounterScripts {

    /**
     * KEYS[1]을 1 증가시키고, 키에 TTL이 없을 때만(첫 증가) ARGV[1] 밀리초 TTL을 건다.
     * 이미 TTL이 있으면 연장하지 않는다. 반환값은 증가 후 값.
     */
    static final RedisScript<Long> INCREMENT_WITH_TTL_ON_FIRST = RedisScript.of("""
            local count = redis.call('INCR', KEYS[1])
            if count == 1 or redis.call('PTTL', KEYS[1]) == -1 then
                redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return count
            """, Long.class);

    private RedisCounterScripts() {
    }

    static long toCount(Long result) {
        return result == null ? 0L : result;
    }
}

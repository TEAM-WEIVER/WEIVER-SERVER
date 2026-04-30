package com.weiver.global.security.jwt.type;

public enum RefreshTokenRotationResult {
    ROTATED,
    NOT_FOUND,
    MISMATCH,
    CONCURRENT_MODIFIED // Redis 값을 읽고 교체하려는 사이에 누군가 먼저 Redis 값을 바꿈(동시 요청 상황 시 발생 가능)
}

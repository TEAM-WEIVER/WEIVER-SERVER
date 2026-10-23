package com.weiver.global.security.jwt.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * JWT의 token_type 클레임 값. 발급 시 넣고, 인증 경로는 ACCESS만 받는다.
 */
@Getter
@RequiredArgsConstructor
public enum TokenType {
    ACCESS("access"),
    REFRESH("refresh");

    private final String claimValue;
}

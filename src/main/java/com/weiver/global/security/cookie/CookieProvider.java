package com.weiver.global.security.cookie;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
@EnableConfigurationProperties(CookieProperties.class)
public class CookieProvider {

    private final CookieProperties cookieProperties;

    public CookieProvider(CookieProperties cookieProperties) {
        this.cookieProperties = cookieProperties;
    }

    public ResponseCookie createRefreshTokenCookie(String refreshToken) {
        return ResponseCookie.from(cookieProperties.name(), refreshToken)
                .path(cookieProperties.path())
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .maxAge(Duration.ofSeconds(cookieProperties.maxAge()))
                .build();
    }

    // 브라우저 refresh token 쿠키 삭제
    public ResponseCookie createExpiredRefreshTokenCookie() {
        return ResponseCookie.from(cookieProperties.name(), "")
                .path(cookieProperties.path())
                .httpOnly(true)
                .secure(cookieProperties.secure())
                .sameSite(cookieProperties.sameSite())
                .maxAge(Duration.ZERO)
                .build();
    }
}

package com.weiver.global.security.cookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RefreshTokenCookieResolver {

    private final CookieProperties cookieProperties;

    public String resolve(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();

        if(cookies == null) return null;

        String refreshTokenCookieName = cookieProperties.name();

        for (Cookie cookie : cookies) {
            if(!refreshTokenCookieName.equals(cookie.getName())) continue;

            String refreshToken = cookie.getValue();

            if(refreshToken == null || refreshToken.isBlank()) return null;

            return refreshToken;
        }

        return null;
    }
}

package com.weiver.global.security.cookie;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cookie.refresh-token")
public record CookieProperties(
        String name,
        String path,
        boolean httpOnly,
        boolean secure,
        String sameSite,
        long maxAge
) {
}

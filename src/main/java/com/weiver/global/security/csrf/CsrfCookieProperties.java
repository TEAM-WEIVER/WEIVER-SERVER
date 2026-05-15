package com.weiver.global.security.csrf;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "cookie.csrf-token")
public record CsrfCookieProperties(
        String cookieName,
        String headerName,
        String path,
        String domain,
        boolean secure,
        String sameSite
) {
}

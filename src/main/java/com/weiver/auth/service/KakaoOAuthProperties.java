package com.weiver.auth.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.StringUtils;

import java.net.URI;

@ConfigurationProperties(prefix = "auth.kakao")
public record KakaoOAuthProperties(
        String clientId,
        String clientSecret,
        URI redirectUri,
        URI frontendReturnUri
) {
    void requireLoginConfiguration() {
        if (!StringUtils.hasText(clientId) || !StringUtils.hasText(clientSecret)
                || !isBrowserUri(redirectUri) || !isBrowserUri(frontendReturnUri)
                || redirectUri.getQuery() != null || frontendReturnUri.getQuery() != null) {
            throw new BusinessException(ErrorCode.KAKAO_OAUTH_UNAVAILABLE);
        }
    }

    private static boolean isBrowserUri(URI uri) {
        return uri != null && uri.getHost() != null && uri.getUserInfo() == null && uri.getFragment() == null
                && ("https".equals(uri.getScheme()) || ("http".equals(uri.getScheme())
                && ("localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost()))));
    }
}

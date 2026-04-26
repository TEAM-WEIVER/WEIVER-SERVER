package com.weiver.global.security.jwt;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

@Component
public class BearerTokenResolver {

    private static final String BEARER_PREFIX = "Bearer ";

    public String resolve(String authorizationHeader) {
        if(authorizationHeader == null || authorizationHeader.isBlank()) {
            return null;
        }

        if(!authorizationHeader.startsWith(BEARER_PREFIX)) {
            throw new BusinessException(ErrorCode.INVALID_TOKEN);
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    public String resolve(HttpServletRequest request) {
        return resolve(request.getHeader(HttpHeaders.AUTHORIZATION));
    }
}

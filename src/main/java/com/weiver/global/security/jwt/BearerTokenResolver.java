package com.weiver.global.security.jwt;

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
            return null;
        }

        return authorizationHeader.substring(BEARER_PREFIX.length());
    }

    public String resolve(HttpServletRequest request) {
        return resolve(request.getHeader(HttpHeaders.AUTHORIZATION));
    }
}

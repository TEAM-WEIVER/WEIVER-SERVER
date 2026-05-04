package com.weiver.global.auth;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/*
    * 서비스 레이어에서 SecurityContext의 principal이 필요한 경우 사용(엔티티 로드)
 */
@Component
public class AuthenticatedPrincipalResolver {

    public AuthenticatedPrincipal resolve(Authentication authentication) {
        if (authentication == null
                || !(authentication.getPrincipal() instanceof AuthenticatedPrincipal principal)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return principal;
    }

    public AuthenticatedPrincipal current() {
        return resolve(SecurityContextHolder.getContext().getAuthentication());
    }
}
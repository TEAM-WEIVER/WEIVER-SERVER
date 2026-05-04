package com.weiver.global.security.principal;

import com.weiver.global.common.UserRole;

public record AuthenticatedPrincipal(
        String publicId,
        UserRole role
) {
}
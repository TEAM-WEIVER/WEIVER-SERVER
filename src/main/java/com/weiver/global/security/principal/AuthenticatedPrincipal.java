package com.weiver.global.security.principal;

import com.weiver.global.common.UserRole;

import java.security.Principal;

public record AuthenticatedPrincipal(
        String publicId,
        UserRole role
) implements Principal {
    @Override
    public String getName() {
        return publicId;
    }
}

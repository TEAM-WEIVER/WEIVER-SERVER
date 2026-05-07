package com.weiver.auth.service.dto;

import com.weiver.global.common.UserRole;

public record CompanyLoginResult(
        String accessToken,
        String refreshToken,
        UserRole role
) {
}

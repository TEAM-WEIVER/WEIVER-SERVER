package com.weiver.auth.service.dto;

import com.weiver.global.common.UserRole;

public record ApplicantLoginResult(
        String accessToken,
        String refreshToken,
        UserRole role
) {
}

package com.weiver.auth.dto.response;

import com.weiver.global.common.UserRole;

public record ApplicantLoginResponseDTO(
        String accessToken,
        UserRole role
) {
}

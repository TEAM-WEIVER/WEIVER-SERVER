package com.weiver.auth.dto.response;

import com.weiver.global.common.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicantLoginResponseDTO(
        @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken,

        @Schema(description = "사용자 권한", example = "APPLICANT")
        UserRole role
) {
}

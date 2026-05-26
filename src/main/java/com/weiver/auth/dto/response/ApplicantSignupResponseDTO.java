package com.weiver.auth.dto.response;

import com.weiver.global.common.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicantSignupResponseDTO(
        @Schema(description = "권한", example = "APPLICANT")
        UserRole role,

        @Schema(description = "JWT Access Token", example = "eyJhbGciOiJIUzI1NiJ9...")
        String accessToken
) {
}

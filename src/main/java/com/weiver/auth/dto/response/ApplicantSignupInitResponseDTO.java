package com.weiver.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicantSignupInitResponseDTO(
        @Schema(description = "회원가입 토큰", example = "123e4567-e89b-12d3-a456-426614174000")
        String signupToken
) {
}

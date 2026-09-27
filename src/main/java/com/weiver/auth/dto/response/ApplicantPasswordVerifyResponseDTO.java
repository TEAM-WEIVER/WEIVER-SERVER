package com.weiver.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicantPasswordVerifyResponseDTO(
        @Schema(description = "비밀번호 변경 2단계에서 사용할 재인증 토큰", example = "123e4567-e89b-12d3-a456-426614174000")
        String reauthToken
) {
}

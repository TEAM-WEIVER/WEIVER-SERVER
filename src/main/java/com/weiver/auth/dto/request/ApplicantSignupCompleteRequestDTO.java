package com.weiver.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record ApplicantSignupCompleteRequestDTO(
        @Schema(description = "회원가입 토큰", example = "123e4567-e89b-12d3-a456-426614174000")
        @NotBlank(message = "회원가입 토큰은 필수 입력값입니다.")
        String signupToken,

        @Schema(description = "약관 동의 정보")
        @Valid
        @NotNull(message = "약관 동의 정보는 필수 입력값입니다.")
        ApplicantAgreementRequestDTO agreements
) {
}

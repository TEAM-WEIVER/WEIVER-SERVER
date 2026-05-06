package com.weiver.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ApplicantLoginRequestDTO(
        @Schema(description = "구직자 이메일", example = "applicant@example.com")
        @NotBlank(message = "이메일은 필수 입력값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "비밀번호", example = "Password123!")
        @NotBlank(message = "비밀번호는 필수 입력값입니다.")
        String password
) {
}

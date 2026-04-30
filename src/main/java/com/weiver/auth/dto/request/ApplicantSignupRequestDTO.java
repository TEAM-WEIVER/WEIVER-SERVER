package com.weiver.auth.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ApplicantSignupRequestDTO(
        @NotBlank(message = "이메일은 필수 입력값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @NotBlank(message = "비밀번호는 필수 입력값입니다.")
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}:;\"'<>,.?/]).{8,64}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다."
        )
        String password,

        @NotBlank(message = "비밀번호 확인은 필수 입력값입니다.")
        String passwordConfirm,

        @NotBlank(message = "이메일 인증 토큰은 필수 입력값입니다.")
        String verificationToken,

        @Valid
        @NotNull(message = "약관 동의 정보는 필수 입력값입니다.")
        ApplicantAgreementRequestDTO agreements
) {
}

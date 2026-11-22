package com.weiver.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ApplicantPasswordSetupRequestDTO(
        @NotBlank String reauthToken,
        @NotBlank @Pattern(regexp = "[0-9]{6}") String emailCode,
        @NotBlank
        @Pattern(
                regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[!@#$%^&*()_+\\-={}:;\"'<>,.?/]).{8,64}$",
                message = "비밀번호는 영문, 숫자, 특수문자를 각각 1자 이상 포함해야 합니다."
        )
        String newPassword,
        @NotBlank String newPasswordConfirm
) {
}

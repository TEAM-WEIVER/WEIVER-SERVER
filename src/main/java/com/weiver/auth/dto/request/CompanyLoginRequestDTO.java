package com.weiver.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompanyLoginRequestDTO(
        @Schema(name = "아이디", example = "weiver")
        @NotBlank(message = "아이디는 필수입니다.")
        @Size(max = 50, message = "아이디는 50자 이하여야 합니다.")
        String loginId,

        @Schema(name = "비밀번호", example = "Password123!")
        @NotBlank(message = "비밀번호는 필수입니다.")
        String password
) {
}

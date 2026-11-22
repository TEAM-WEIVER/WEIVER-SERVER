package com.weiver.auth.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KakaoSignupCompleteRequestDTO(
        @NotBlank String signupToken,
        @Email @Size(max = 255) String email,
        String verificationToken,
        @NotNull @Valid ApplicantAgreementRequestDTO agreements
) {
}

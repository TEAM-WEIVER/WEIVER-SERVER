package com.weiver.auth.dto.response;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.UserRole;
import io.swagger.v3.oas.annotations.media.Schema;

public record ApplicantSignupResponseDTO(
        @Schema(description = "외부 노출용 식별자인 publicId", example = "550e8400-e29b...")
        String publicId,

        @Schema(description = "권한", example = "Applicant")
        UserRole role
) {
    public static ApplicantSignupResponseDTO from(Applicant applicant) {
        return new ApplicantSignupResponseDTO(
                applicant.getPublicId(),
                applicant.getRole()
        );
    }
}

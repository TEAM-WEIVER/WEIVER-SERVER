package com.weiver.auth.dto.response;

import com.weiver.applicant.domain.Applicant;
import com.weiver.global.common.UserRole;

public record ApplicantSignupResponseDTO(
        Long applicantId,
        String email,
        UserRole role
) {
    public static ApplicantSignupResponseDTO from(Applicant applicant) {
        return new ApplicantSignupResponseDTO(
                applicant.getApplicantId(),
                applicant.getEmail(),
                applicant.getRole()
        );
    }
}

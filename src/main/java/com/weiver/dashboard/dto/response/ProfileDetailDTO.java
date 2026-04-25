package com.weiver.dashboard.dto.response;

import com.weiver.applicant.domain.Applicant;

public record ProfileDetailDTO (
        Long applicantId,
        String name,
        String phoneNumber,
        String email,
        String photoUrl,
        String position // 직급 (WorkExperiences)
){
    public static ProfileDetailDTO of(Applicant applicant, String position) {
        return new ProfileDetailDTO(
                applicant.getApplicantId(),
                applicant.getName(),
                applicant.getPhoneNumber(),
                applicant.getEmail(),
                applicant.getPhotoUrl(),
                position
        );
    }
}

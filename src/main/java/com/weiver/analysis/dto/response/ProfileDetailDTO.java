package com.weiver.analysis.dto.response;

import com.weiver.applicant.domain.Applicant;

public record ProfileDetailDTO (
        Long applicantId,
        String name,
        String phoneNumber,
        String email,
        String photoUrl,
        String position // 학력 (WorkExperiences)
){
    public static ProfileDetailDTO from(Applicant applicant, String position) {
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

package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Applicant;

public record ApplicantDetailResponseDTO(
        String photoUrl,
        String name,
        String birthday,
        String phoneNumber,
        String email
) {
    public static ApplicantDetailResponseDTO from(Applicant applicant){
        return new ApplicantDetailResponseDTO(
                applicant.getPhotoUrl(),
                applicant.getName(),
                applicant.getBirthday().toString(),
                applicant.getPhoneNumber(),
                applicant.getEmail()
        );
    }
}

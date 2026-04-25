package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.WorkExperience;

public record WorkExperienceDetailResponseDTO(
        Long experienceId,
        String companyName,
        String position,
        String startDate,
        String endDate,
        String duties,
        String employmentType
) {
    public static WorkExperienceDetailResponseDTO from(WorkExperience workExperience) {
        return new WorkExperienceDetailResponseDTO(
                workExperience.getExperienceId(),
                workExperience.getCompanyName(),
                workExperience.getPosition(),
                workExperience.getStartDate().toString(),
                workExperience.getEndDate() != null ? workExperience.getEndDate().toString() : null,
                workExperience.getDuties(),
                workExperience.getEmploymentType().name()
        );
    }
}

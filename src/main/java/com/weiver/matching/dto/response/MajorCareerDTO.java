package com.weiver.matching.dto.response;

import com.weiver.applicant.domain.WorkExperience;

import java.time.LocalDate;

public record MajorCareerDTO(
        Long experienceId,
        String companyName,
        String position,
        String employeeType,
        LocalDate startDate,
        LocalDate endDate,
        String duties
) {
    public static MajorCareerDTO from(WorkExperience workExperience) {
        return new MajorCareerDTO(
                workExperience.getExperienceId(),
                workExperience.getCompanyName(),
                workExperience.getPosition(),
                workExperience.getEmploymentType().getDescription(),
                workExperience.getStartDate(),
                workExperience.getEndDate(),
                workExperience.getDuties()
        );
    }
}

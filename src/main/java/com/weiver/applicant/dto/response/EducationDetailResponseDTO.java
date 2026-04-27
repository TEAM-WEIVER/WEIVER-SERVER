package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Education;

public record EducationDetailResponseDTO(
        Long educationId,
        String schoolName,
        String degree,
        String major,
        Double gpa,
        String startDate,
        String endDate,
        String status
) {
    public static EducationDetailResponseDTO from(Education education) {
        return new EducationDetailResponseDTO(
                education.getEducationId(),
                education.getSchoolName(),
                education.getDegree().name(),
                education.getMajor(),
                education.getGpa() != null ? education.getGpa().doubleValue() : null,
                education.getStartDate().toString(),
                education.getEndDate() != null ? education.getEndDate().toString() : null,
                education.getStatus().name()
        );
    }
}

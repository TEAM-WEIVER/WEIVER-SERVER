package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Education;

public record EducationDetailResponseDTO(
        long educationId,
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
                education.getGpa().doubleValue(),
                education.getStartDate().toString(),
                education.getEndDate().toString(),
                education.getStatus().name()
        );
    }
}

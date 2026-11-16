package com.weiver.applicant.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record ApplicantProfileChangedData(
        @JsonProperty("applicant_id")
        Long applicantId,
        @JsonProperty("applicant_name")
        String applicantName,
        List<EducationData> educations,
        List<ExperienceData> experiences,
        List<String> certifications,
        List<EssayData> essay
) {
    public record EducationData(
            Long id,
            @JsonProperty("education_level") String degree,
            @JsonProperty("school_name") String schoolName,
            String major,
            BigDecimal gpa,
            @JsonProperty("start_date") String startDate,
            @JsonProperty("end_date") String endDate,
            @JsonProperty("graduation_status") String status
    ) {
    }

    public record ExperienceData(
            Long id,
            @JsonProperty("experience_name") String companyName,
            @JsonProperty("start_date") LocalDate startDate,
            @JsonProperty("end_date") LocalDate endDate,
            @JsonProperty("employment_type") String employmentType,
            @JsonProperty("position_title") String position,
            @JsonProperty("responsibilities") String duties,
            @JsonProperty("is_recognized") boolean recognized
    ) {
    }

    public record EssayData(
            Long id,
            String question,
            String answer
    ) {
    }
}

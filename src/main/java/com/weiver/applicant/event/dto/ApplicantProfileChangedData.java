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
        List<EssayData> essay,
        PortfolioData portfolio
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

    /**
     * 포트폴리오. 지원자가 포트폴리오를 등록하지 않았으면 상위 {@code portfolio} 자체가 null이다.
     * {@code file}은 PDF 파일이 없거나 PDF가 아니면 null이다(링크는 담지 않는다).
     */
    public record PortfolioData(
            @JsonProperty("portfolio_id") Long portfolioId,
            PortfolioFileData file
    ) {
    }

    /** AI 서버가 S3에서 직접 내려받을 PDF 위치와 원본 파일 정보. */
    public record PortfolioFileData(
            String bucket,
            String key,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("file_size") Long fileSize
    ) {
    }
}

package com.weiver.analysis.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.util.List;

/**
 * 지원자 분석 요청(APPLICANT_ANALYSIS_REQUESTED) data. 제출 시점의 프로필 전체 스냅샷이다.
 *
 * <p>AI가 쓰지 않는 필드(학력 id·gpa·날짜, 경력 id·is_recognized, 자격증 점수·등급, 수상 내용,
 * 포트폴리오 제목)는 담지 않는다. null 값도 필드는 직렬화에 남긴다.
 */
public record ApplicantAnalysisRequestedData(
        @JsonProperty("applicant_id")
        Long applicantId,
        @JsonProperty("submission_version")
        Integer submissionVersion,
        @JsonProperty("applicant_name")
        String applicantName,
        List<EducationData> educations,
        List<ExperienceData> experiences,
        /** 자격증 이름 목록. */
        List<String> certifications,
        List<EssayData> essay,
        /** 수상 이력. 없으면 빈 배열이다. */
        List<AwardData> awards,
        /** 포트폴리오를 등록하지 않았으면 null이다. */
        PortfolioData portfolio
) {
    public record EducationData(
            @JsonProperty("education_level") String educationLevel,
            @JsonProperty("school_name") String schoolName,
            String major,
            @JsonProperty("graduation_status") String graduationStatus
    ) {
    }

    public record ExperienceData(
            @JsonProperty("experience_name") String experienceName,
            @JsonProperty("position_title") String positionTitle,
            @JsonProperty("employment_type") String employmentType,
            @JsonProperty("start_date") LocalDate startDate,
            /** 재직 중이면 null. */
            @JsonProperty("end_date") LocalDate endDate,
            /** 담당 업무를 적지 않았으면 null. */
            String responsibilities
    ) {
    }

    public record EssayData(
            Long id,
            String question,
            String answer
    ) {
    }

    public record AwardData(
            Long id,
            @JsonProperty("award_name") String awardName,
            /** 발행 기관을 적지 않았으면 null. */
            String issuer,
            @JsonProperty("award_date") LocalDate awardDate
    ) {
    }

    /**
     * 포트폴리오. {@code file}은 PDF 파일이 없거나 PDF가 아니거나 저장된 URL을 해석할 수 없으면 null이다.
     */
    public record PortfolioData(
            @JsonProperty("portfolio_id") Long portfolioId,
            PortfolioFileData file
    ) {
    }

    /**
     * AI 서버가 S3에서 직접 내려받을 PDF 위치와 원본 파일 정보. {@code key}는 디코딩된 객체 키다.
     * {@code version_id}는 버전 관리를 쓰지 않으므로 항상 JSON null이다(빈 문자열 금지).
     */
    public record PortfolioFileData(
            String bucket,
            String key,
            @JsonProperty("file_name") String fileName,
            @JsonProperty("file_size") Long fileSize,
            @JsonProperty("version_id") String versionId
    ) {
        public static PortfolioFileData of(String bucket, String key, String fileName, Long fileSize) {
            return new PortfolioFileData(bucket, key, fileName, fileSize, null);
        }
    }
}

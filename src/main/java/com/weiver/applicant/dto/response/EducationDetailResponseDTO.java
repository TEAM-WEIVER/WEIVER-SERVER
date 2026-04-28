package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Education;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "학력 정보 상세 응답 DTO")
public record EducationDetailResponseDTO(

        @Schema(description = "학력 ID", example = "1")
        Long educationId,

        @Schema(description = "학교명", example = "한양대학교 에리카")
        String schoolName,

        @Schema(description = "학위 (예: BACHELOR, MASTER 등)", example = "BACHELOR")
        String degree,

        @Schema(description = "전공", example = "ICT융합학부")
        String major,

        @Schema(description = "학점 (입력하지 않았을 경우 null)", example = "4.2", nullable = true)
        Double gpa,

        @Schema(description = "입학/시작 년월 (YYYY-MM 형식)", example = "2021-03")
        String startDate,

        @Schema(description = "졸업/종료 년월 (재학 중일 경우 null)", example = "2026-02", nullable = true)
        String endDate,

        @Schema(description = "재학 상태 (예: ACTIVE, GRADUATED 등)", example = "ACTIVE")
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
package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.WorkExperience;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "경력 정보 상세 응답 DTO")
public record WorkExperienceDetailResponseDTO(

        @Schema(description = "경력 ID", example = "1")
        Long experienceId,

        @Schema(description = "회사명", example = "에이블리")
        String companyName,

        @Schema(description = "직급", example = "인턴")
        String position,

        @Schema(description = "입사 날짜 (YYYY-MM-DD 형식)", example = "2026-02-01")
        String startDate,

        @Schema(description = "퇴사 날짜 (현재 재직 중일 경우 null)", example = "2026-08-01", nullable = true)
        String endDate,

        @Schema(description = "담당 업무 내용", example = "B2B 서비스 백엔드 API 설계 및 개발")
        String duties,

        @Schema(description = "고용 형태 (예: INTERN, FULL_TIME 등)", example = "INTERN")
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
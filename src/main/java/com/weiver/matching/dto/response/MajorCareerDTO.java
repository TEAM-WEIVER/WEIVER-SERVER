package com.weiver.matching.dto.response;

import com.weiver.applicant.domain.WorkExperience;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

@Schema(description = "지원자 주요 경력 요약 DTO")
public record MajorCareerDTO(
        @Schema(description = "경력 고유 ID", example = "3")
        Long experienceId,

        @Schema(description = "근무 회사명", example = "쿠팡플레이")
        String companyName,

        @Schema(description = "해당 경력에서의 직급 또는 포지션", example = "백엔드 개발자")
        String position,

        @Schema(description = "고용 형태 설명값입니다.", example = "정규직")
        String employeeType,

        @Schema(description = "근무 시작일", example = "2022-03-01", type = "string", format = "date")
        LocalDate startDate,

        @Schema(description = "근무 종료일입니다. 현재 재직 중인 경력은 null일 수 있습니다.", example = "2025-02-28", type = "string", format = "date", nullable = true)
        LocalDate endDate,

        @Schema(description = "주요 담당 업무 또는 성과 요약", example = "대용량 트래픽을 처리하는 주문 API와 정산 배치 시스템을 개발했습니다.")
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

package com.weiver.applicant.dto.request.put;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.type.EmploymentType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "경력 수정 상세 정보 DTO")
public record WorkExperienceUpdateDetailDTO(

        @Schema(description = "경력 고유 ID (기존 데이터 수정 시 필수, 신규 추가 시 null)", example = "1")
        Long workExperienceId,

        @Schema(description = "회사명", example = "쿠팡플레이")
        @NotBlank(message = "회사명은 필수입니다.")
        String companyName,

        @Schema(description = "입사 날짜", example = "2019-03-01", type = "string")
        @NotNull(message = "입사 날짜는 필수 입력값입니다.")
        LocalDate startDate,

        @Schema(description = "퇴사 날짜", example = "2024-08-01", type = "string")
        LocalDate endDate,

        @Schema(description = "경력", example = "FULL_TIME",
                allowableValues = {"FULL_TIME", "INTERN", "CONTRACT", "FREELANCER", "MILITARY_SERVICE_EXEMPTION", "PART_TIME"})
        @NotBlank(message = "경력 형태는 필수 입력값입니다.")
        String employmentType,

        @Schema(description = "직급", example = "대리")
        @NotBlank(message = "직급은 필수 입력값입니다.")
        String position,

        @Schema(description = "담당 업무", example = "백앤드 개발자로써 축구 도메인에서 서버 개발을 맡았음")
        @NotBlank(message = "담당 업무는 필수 입력값입니다.")
        String duties,

        @Schema(description = "해당 경력 활용 할건지의 여부", example = "true")
        @NotNull(message = "경력 여부는 필수 입력값입니다.")
        Boolean isRecognized) {
    public WorkExperience toEntity(Applicant applicant){
        return WorkExperience.builder()
            .companyName(this.companyName())
            .startDate(this.startDate())
            .endDate(this.endDate())
            .employmentType(EmploymentType.valueOf(this.employmentType()))
            .position(this.position())
            .duties(this.duties())
            .isRecognized(this.isRecognized())
            .applicant(applicant)
            .build();
    }
}

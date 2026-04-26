package com.weiver.applicant.dto.request.put;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.type.EmploymentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

public record WorkExperienceUpdateDetailDTO(
    Long workExperienceId,
    @NotBlank(message = "회사명은 필수입니다.")
    String companyName,
    @NotBlank(message = "재직 날짜는 필수 입력값입니다.")
    LocalDate startDate,
    LocalDate endDate,
    @NotBlank(message = "경력 형태는 필수 입력값입니다.")
    String employmentType,
    @NotBlank(message = "직급은 필수 입력값입니다.")
    String position,
    @NotBlank(message = "담당 업무는 필수 입력값입니다.")
    String duties,
    @NotNull(message = "경력 여부는 필수 입력값입니다.")
    Boolean isRecognized){
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

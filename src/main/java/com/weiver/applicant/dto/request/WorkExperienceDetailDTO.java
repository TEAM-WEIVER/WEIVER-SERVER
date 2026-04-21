package com.weiver.applicant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record WorkExperienceDetailDTO (
    @NotBlank(message = "회사명은 필수입니다.")
    String companyName,
    @NotBlank(message = "재직 날짜는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{4}\\.\\d{2}\\.\\d{2}$", message = "재직 날짜는 YYYY.MM.DD 형식이어야 합니다.")
    String startDate,
    @Pattern(regexp = "^\\d{4}\\.\\d{2}\\.\\d{2}$", message = "퇴직 날짜는 YYYY.MM.DD 형식이어야 합니다.")
    String endDate,
    @NotBlank(message = "경력 형태는 필수 입력값입니다.")
    String employmentType,
    @NotBlank(message = "직급은 필수 입력값입니다.")
    String position,
    @NotBlank(message = "담당 업무는 필수 입력값입니다.")
    String duties,
    @NotNull(message = "경력 여부는 필수 입력값입니다.")
    Boolean isRecognized){}

package com.weiver.applicant.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class EducationDetailDTO {

    @NotBlank(message = "학력 유형은 필수 입력값입니다.")
    private String degreeType;

    @NotBlank(message = "학교 이름은 필수 입력값입니다.")
    private String schoolName;

    @NotBlank(message = "전공은 필수 입력값입니다.")
    private String major;

    @NotNull(message = "학점은 필수 입력값입니다.")
    private Double gpa;

    @NotBlank(message = "입학 날짜는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{4}\\.\\d{2}$", message = "입학 날짜는 YYYY.MM 형식이어야 합니다.")
    private String startDate;

    @NotBlank(message = "졸업 날짜는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{4}\\.\\d{2}$", message = "졸업 날짜는 YYYY.MM 형식이어야 합니다.")
    private String endDate;

    @NotBlank(message = "학력 상태는 필수 입력값입니다.")
    private String status;

}

package com.weiver.applicant.dto.request.post;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.type.Degree;
import com.weiver.applicant.type.Status;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;

public record EducationDetailDTO (

    @NotBlank(message = "학력 유형은 필수 입력값입니다.")
    String degreeType,
    @NotBlank(message = "학교 이름은 필수 입력값입니다.")
    String schoolName,
    @NotBlank(message = "전공은 필수 입력값입니다.")
    String major,
    @NotNull(message = "학점은 필수 입력값입니다.")
    Double gpa,
    @NotNull(message = "입학 날짜는 필수 입력값입니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM", timezone = "Asia/Seoul")
    YearMonth startDate,
    @NotNull(message = "졸업 날짜는 필수 입력값입니다.")
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM", timezone = "Asia/Seoul")
    YearMonth endDate,
    @NotBlank(message = "학력 상태는 필수 입력값입니다.")
    String status){
    public Education toEntity(Applicant applicant){
        return Education.builder()
                .applicant(applicant)
                .degree(Degree.valueOf(degreeType))
                .schoolName(schoolName)
                .major(major)
                .gpa(BigDecimal.valueOf(gpa))
                .startDate(startDate)
                .endDate(endDate)
                .status(Status.valueOf(status))
                .build();
    }
}

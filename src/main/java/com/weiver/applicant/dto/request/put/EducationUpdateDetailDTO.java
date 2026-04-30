package com.weiver.applicant.dto.request.put;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.type.Degree;
import com.weiver.applicant.type.Status;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.YearMonth;

@Schema(description = "학력 수정 상세 정보 DTO")
public record EducationUpdateDetailDTO(

        @Schema(description = "학력 고유 ID (기존 데이터 수정 시 필수, 신규 추가 시 null)", example = "1")
        Long educationId,

        @Schema(description = "학력", example = "ASSOCIATE",
                allowableValues = {"HIGH_SCHOOL", "ASSOCIATE", "BACHELOR", "MASTER", "DOCTOR"})
        @NotBlank(message = "학력 유형은 필수 입력값입니다.")
        String degreeType,

        @Schema(description = "학교 이름", example = "한양대학교")
        @NotBlank(message = "학교 이름은 필수 입력값입니다.")
        String schoolName,

        @Schema(description = "전공", example = "컴퓨터학부")
        @NotBlank(message = "전공은 필수 입력값입니다.")
        String major,

        @Schema(description = "학점", example = "4.1")
        @NotNull(message = "학점은 필수 입력값입니다.")
        Double gpa,

        @Schema(description = "입학 날짜",  example = "2021-03", type = "string")
        @NotNull(message = "입학 날짜는 필수 입력값입니다.")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM", timezone = "Asia/Seoul")
        YearMonth startDate,

        @Schema(description = "졸업 날짜",  example = "2027-03", type = "string")
        @NotNull(message = "졸업 날짜는 필수 입력값입니다.")
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM", timezone = "Asia/Seoul")
        YearMonth endDate,

        @Schema(description = "학력", example = "ACTIVE",
                allowableValues = {"GRADUATED", "LEAVE_OF_ABSENCE", "GRADUATION_POSTPONED", "ACTIVE"})
        @NotBlank(message = "학력 상태는 필수 입력값입니다.")
        String status) {
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

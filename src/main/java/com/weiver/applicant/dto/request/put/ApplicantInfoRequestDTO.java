package com.weiver.applicant.dto.request.put;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

@Schema(description = "구직자 기본 정보 수정 요청 DTO")
public record ApplicantInfoRequestDTO(

        @Schema(description = "지원자 실명", example = "이현우")
        @NotBlank(message = "이름은 필수 입력값입니다.")
        String name,

        @Schema(description = "연락 가능한 이메일 주소", example = "weiver@example.com")
        @NotBlank(message = "이메일은 필수 입력값입니다.")
        @Email(message = "이메일 형식이 올바르지 않습니다.")
        String email,

        @Schema(description = "휴대폰 번호 (하이픈 포함)", example = "010-1234-5678")
        @NotBlank(message = "전화번호는 필수 입력값입니다.")
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "휴대폰 번호 양식에 맞지 않습니다.")
        String phoneNumber,

        @Schema(description = "현재 거주지 주소", example = "경기도 안산시 상록구 한양대학로 55")
        @NotBlank(message = "주소는 필수 입력값입니다.")
        String address,

        @Schema(description = "생년월일 (YYYY-MM-DD 형식)", example = "2000-01-01", type = "string")
        @NotNull(message = "생년월일은 필수 입력값입니다.")
        LocalDate birthday
) {}
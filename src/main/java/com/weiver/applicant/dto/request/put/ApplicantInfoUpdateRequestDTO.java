package com.weiver.applicant.dto.request.put;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;


// applicantId JWT 토큰을 통해서 받아옴
public record ApplicantInfoUpdateRequestDTO(
        @NotNull
        String profileImageUrl,
        @NotBlank(message = "이름은 필수 입력값입니다.")
        String name,
        @NotBlank(message = "이메일은 필수 입력값입니다.")
        @Email(message = "이메일 형식이 옳바르지 않습니다.")
        String email,
        @NotBlank(message = "전화번호는 필수 입력값입니다.")
        @Pattern(regexp = "^\\d{2,3}-\\d{3,4}-\\d{4}$", message = "휴대폰 번호 양식에 맞지 않습니다.")
        String phoneNumber,
        @NotBlank(message = "주소는 필수 입력값입니다.")
        String address,
        @NotBlank(message = "생년월일은 필수 입력값입니다.")
        @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "생년월일은 YYYY-MM-DD 형식이어야 합니다.")
        LocalDate birthday){}

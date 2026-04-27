package com.weiver.applicant.dto.request.put;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record ApplicantInfoRequestDTO(
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
    LocalDate birthday){}

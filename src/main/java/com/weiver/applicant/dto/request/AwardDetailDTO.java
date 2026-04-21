package com.weiver.applicant.dto.request;


import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class AwardDetailDTO {

    @NotBlank(message = "취득 날짜는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{4}\\.\\d{2}\\.\\d{2}$", message = "취득 날짜는 YYYY.MM.DD 형식이어야 합니다.")
    private String awardDate;

    @NotBlank(message = "수상 이름은 필수 입력값입니다.")
    private String awardName;

    @NotBlank(message = "발급 기관은 필수 입력값입니다.")
    private String issuer;
}

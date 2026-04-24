package com.weiver.applicant.dto.request.post;


import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.LocalDate;

public record AwardDetailDTO (
    @NotBlank(message = "취득 날짜는 필수 입력값입니다.")
    @Pattern(regexp = "^\\d{4}-\\d{2}-\\d{2}$", message = "취득 날짜는 YYYY-MM-DD 형식이어야 합니다.")
    LocalDate awardDate,
    @NotBlank(message = "수상 이름은 필수 입력값입니다.")
    String awardName,
    @NotBlank(message = "발급 기관은 필수 입력값입니다.")
    String issuer){
    public Award toEntity(Applicant applicant){
        return Award.builder()
                .awardDate(this.awardDate)
                .awardName(this.awardName)
                .issuer(this.issuer)
                .applicant(applicant)
                .build();
    }
}

package com.weiver.applicant.dto.request.post;


import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "수상 생성 상세 정보 DTO")
public record AwardDetailDTO (

        @Schema(description = "수상 날짜", example = "2000-01-01", type = "string")
        @NotNull(message = "수상 날짜는 필수 입력값입니다.")
        LocalDate awardDate,

        @Schema(description = "수상 이름", example = "최우수상")
        @NotBlank(message = "수상 이름은 필수 입력값입니다.")
        String awardName,

        @Schema(description = "발급 기관", example = "한국인터넷진흥원장")
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

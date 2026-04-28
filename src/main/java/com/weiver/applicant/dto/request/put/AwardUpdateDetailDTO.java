package com.weiver.applicant.dto.request.put;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

@Schema(description = "수상 이력 수정 상세 정보 DTO")
public record AwardUpdateDetailDTO(

        @Schema(description = "수상 이력 고유 ID (기존 데이터 수정 시 필수, 신규 추가 시 null)", example = "1")
        Long awardId,

        @Schema(description = "수상 날짜", example = "2025-11-25", type = "string")
        @NotNull(message = "취득 날짜는 필수 입력값입니다.")
        LocalDate awardDate,

        @Schema(description = "수상 이름", example = "소개딩 최우수상")
        @NotBlank(message = "수상 이름은 필수 입력값입니다.")
        String awardName,

        @Schema(description = "발급 기관 및 주최측", example = "한국인터넷진흥원")
        @NotBlank(message = "발급 기관은 필수 입력값입니다.")
        String issuer){

    public Award toEntity(Applicant applicant) {
        return Award.builder()
                .awardDate(this.awardDate())
                .awardName(this.awardName())
                .issuer(this.issuer())
                .applicant(applicant)
                .build();
    }
}

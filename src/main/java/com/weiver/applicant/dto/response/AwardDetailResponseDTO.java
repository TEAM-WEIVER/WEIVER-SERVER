package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Award;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "수상 이력 상세 응답 DTO")
public record AwardDetailResponseDTO(

        @Schema(description = "수상 이력 ID", example = "1")
        Long awardId,

        @Schema(description = "수상 명칭", example = "한국인터넷진흥원장상")
        String awardName,

        @Schema(description = "수상 날짜 (YYYY-MM-DD 형식)", example = "2025-11-01")
        String awardDate,

        @Schema(description = "발급 기관", example = "한국인터넷진흥원(KISA)")
        String issuer
) {
    public static AwardDetailResponseDTO from(Award award){
        return new AwardDetailResponseDTO(
                award.getAwardId(),
                award.getAwardName(),
                award.getAwardDate() != null ? award.getAwardDate().toString() : null, // 💡 방어 로직 추가
                award.getIssuer()
        );
    }
}
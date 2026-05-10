package com.weiver.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원자 카드 요약 응답 DTO")
public record ApplicantCardResponseDTO(
        @Schema(description = "지원자 기본 프로필 정보")
        @JsonProperty("profile")
        ProfileDetailDTO profileDetailDTO,

        @Schema(description = "매칭 카드에 노출되는 스킬핏, 컬처핏, 기술 태그 정보")
        @JsonProperty("card")
        CardDetailDTO cardDetailDTO,

        @Schema(description = "기업 담당자가 해당 지원자에 대해 남긴 내부 메모", example = "백엔드 경험이 공고 요구사항과 잘 맞아 보입니다.", nullable = true)
        @JsonProperty("memo")
        String memo
) {
    public static ApplicantCardResponseDTO of(ProfileDetailDTO profile, CardDetailDTO card, String memo) {
        return new ApplicantCardResponseDTO(profile, card, memo);
    }
}

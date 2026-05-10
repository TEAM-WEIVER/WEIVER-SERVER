package com.weiver.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApplicantCardResponseDTO(
        @JsonProperty("profile")
        ProfileDetailDTO profileDetailDTO,
        @JsonProperty("card")
        CardDetailDTO cardDetailDTO,
        @JsonProperty("memo")
        String memo
) {
    public static ApplicantCardResponseDTO of(ProfileDetailDTO profile, CardDetailDTO card, String memo) {
        return new ApplicantCardResponseDTO(profile, card, memo);
    }
}

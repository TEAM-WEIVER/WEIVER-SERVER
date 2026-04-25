package com.weiver.dashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ApplicantCardResponseDTO(
        @JsonProperty("profile")
        ProfileDetailDTO profileDetailDTO,
        @JsonProperty("card")
        CardDetailDTO cardDetailDTO
) {
    public static ApplicantCardResponseDTO of(ProfileDetailDTO profile, CardDetailDTO card) {
        return new ApplicantCardResponseDTO(profile, card);
    }
}

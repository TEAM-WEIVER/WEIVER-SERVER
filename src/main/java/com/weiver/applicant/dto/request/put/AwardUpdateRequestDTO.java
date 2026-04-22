package com.weiver.applicant.dto.request.put;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.List;

public record AwardUpdateRequestDTO(
    @JsonProperty("AwardUpdateDTO")
    @Valid
    List<AwardUpdateDetailDTO> awardList
) {}

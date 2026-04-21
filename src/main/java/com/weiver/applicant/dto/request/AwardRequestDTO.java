package com.weiver.applicant.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.List;

public record AwardRequestDTO (
    @JsonProperty("AwardDTO")
    @Valid
    List<AwardDetailDTO> educationList
) {}

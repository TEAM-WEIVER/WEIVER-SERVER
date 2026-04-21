package com.weiver.applicant.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.List;

public record EducationRequestDTO (
    @Valid
    @JsonProperty("EducationDTO")
    List<EducationDetailDTO> educationList){}

package com.weiver.applicant.dto.request.put;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EducationUpdateRequestDTO(
    @Valid @NotNull
    @JsonProperty("EducationUpdateDTO")
    List<EducationUpdateDetailDTO> educationList){}

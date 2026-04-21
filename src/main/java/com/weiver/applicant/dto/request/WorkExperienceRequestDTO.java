package com.weiver.applicant.dto.request;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.List;

public record WorkExperienceRequestDTO (

    @JsonProperty("WorkExperienceDTO")
    @Valid
    List<WorkExperienceDetailDTO> workExperiences){}

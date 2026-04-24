package com.weiver.applicant.dto.request.put;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.List;

public record WorkExperienceUpdateRequestDTO(
    @JsonProperty("WorkExperienceUpdateDTO")
    @Valid
    List<WorkExperienceUpdateDetailDTO> workExperienceList){}

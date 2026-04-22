package com.weiver.applicant.dto.request.put;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.dto.request.post.WorkExperienceDetailDTO;
import jakarta.validation.Valid;

import java.util.List;

public record WorkExperienceUpdateRequestDTO(
    @JsonProperty("WorkExperienceUpdateDTO")
    @Valid
    List<WorkExperienceUpdateDetailDTO> workExperienceList){}

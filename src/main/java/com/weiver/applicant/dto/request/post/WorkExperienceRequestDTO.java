package com.weiver.applicant.dto.request.post;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.WorkExperience;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record WorkExperienceRequestDTO (
    @Valid @NotNull
    @JsonProperty("WorkExperienceDTO")
    List<WorkExperienceDetailDTO> workExperiences){
    public List<WorkExperience> toEntityList(Applicant applicant){
        return this.workExperiences().stream()
            .map(workExperienceDetailDTO -> workExperienceDetailDTO.toEntity(applicant))
            .toList();
    }
}

package com.weiver.applicant.dto.request.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Education;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record EducationRequestDTO (
    @Valid @NotNull
    @JsonProperty("EducationDTO")
    List<EducationDetailDTO> educationList){
    public List<Education> toEntityList(Applicant applicant){
        return educationList.stream()
                .map(educationDetailDTO -> educationDetailDTO.toEntity(applicant))
                .toList();
    }
}

package com.weiver.applicant.dto.request.put;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.dto.request.post.EducationDetailDTO;
import jakarta.validation.Valid;

import java.util.List;

public record EducationUpdateRequestDTO(
    @Valid
    @JsonProperty("EducationUpdateDTO")
    List<EducationUpdateDetailDTO> educationList){}

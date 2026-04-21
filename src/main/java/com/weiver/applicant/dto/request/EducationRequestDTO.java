package com.weiver.applicant.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
public class EducationRequestDTO {
    @JsonProperty("EducationDTO")
    private List<EducationDetailDTO> educationList;
}

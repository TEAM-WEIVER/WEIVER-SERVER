package com.weiver.applicant.dto.request.post;


import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.WorkExperience;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "경력 다건 등록 요청 DTO (래퍼 객체)")
public record WorkExperienceRequestDTO (
        @Schema(description = "경력 배열 (주의: JSON Key 이름은 'WorkExperienceDTO' 입니다.)")
        @Valid @NotNull
        @JsonProperty("WorkExperienceDTO")
        List<WorkExperienceDetailDTO> workExperiences){
    public List<WorkExperience> toEntityList(Applicant applicant){
        return this.workExperiences().stream()
            .map(workExperienceDetailDTO -> workExperienceDetailDTO.toEntity(applicant))
            .toList();
    }
}

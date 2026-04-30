package com.weiver.applicant.dto.request.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Education;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "경력 다건 등록 요청 DTO (래퍼 객체)")
public record EducationRequestDTO (
        @Schema(description = "경력 배열 (주의: JSON Key 이름은 'EducationDTO' 입니다.)")
        @Valid @NotNull
        @JsonProperty("EducationDTO")
        List<EducationDetailDTO> educationList){
    public List<Education> toEntityList(Applicant applicant){
        return educationList.stream()
                .map(educationDetailDTO -> educationDetailDTO.toEntity(applicant))
                .toList();
    }
}

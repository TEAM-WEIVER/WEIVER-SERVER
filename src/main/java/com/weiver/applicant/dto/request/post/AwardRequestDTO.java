package com.weiver.applicant.dto.request.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import jakarta.validation.Valid;

import java.util.List;

public record AwardRequestDTO (
    @JsonProperty("AwardDTO")
    @Valid
    List<AwardDetailDTO> awardList
) {
    public List<Award> toEntityList(Applicant applicant) {
        return this.awardList.stream()
                .map(detailDTO -> detailDTO.toEntity(applicant))
                .toList();
    }
}

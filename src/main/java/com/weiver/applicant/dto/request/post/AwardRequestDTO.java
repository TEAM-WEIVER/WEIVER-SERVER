package com.weiver.applicant.dto.request.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "수상 이력 다건 등록 요청 DTO (래퍼 객체)")
public record AwardRequestDTO (
        @Schema(description = "수상 이력 배열 (주의: JSON Key 이름은 'AwardDTO' 입니다.)")
        @Valid @NotNull
        @JsonProperty("AwardDTO")
        List<AwardDetailDTO> awardList) {
    public List<Award> toEntityList(Applicant applicant) {
        return this.awardList.stream()
                .map(detailDTO -> detailDTO.toEntity(applicant))
                .toList();
    }
}

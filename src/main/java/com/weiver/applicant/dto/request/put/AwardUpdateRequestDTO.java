package com.weiver.applicant.dto.request.put;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "수상 이력 다건 수정 요청 DTO (래퍼 객체)")
public record AwardUpdateRequestDTO(
        @Schema(description = "수상 이력 배열 (주의: JSON Key 이름은 'AwardUpdateDTO' 입니다.)")
        @Valid @NotNull
        @JsonProperty("AwardUpdateDTO")
        List<AwardUpdateDetailDTO> awardList
) {}

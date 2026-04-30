package com.weiver.applicant.dto.request.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Certificate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Schema(description = "자격증 다건 등록 요청 DTO (래퍼 객체)")
public record CertificateRequestDTO (
        @Schema(description = "자격증 이력 배열 (주의: JSON Key 이름은 'CertificateDTO' 입니다.)")
        @Valid @NotNull
        @JsonProperty("CertificateDTO")
        List<CertificateDetailDTO> certificateList){
    public List<Certificate> toEntityList(Applicant applicant){
        return this.certificateList.stream()
                .map(detailDTO -> detailDTO.toEntity(applicant))
                .toList();
    }
}

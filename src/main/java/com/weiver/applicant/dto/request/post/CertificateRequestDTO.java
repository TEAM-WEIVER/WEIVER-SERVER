package com.weiver.applicant.dto.request.post;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Certificate;
import jakarta.validation.Valid;

import java.util.List;

public record CertificateRequestDTO (
    @JsonProperty("CertificateDTO")
    @Valid
    List<CertificateDetailDTO> certificateList){
    public List<Certificate> toEntityList(Applicant applicant){
        return this.certificateList.stream()
                .map(detailDTO -> detailDTO.toEntity(applicant))
                .toList();
    }
}

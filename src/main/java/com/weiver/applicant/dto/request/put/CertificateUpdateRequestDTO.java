package com.weiver.applicant.dto.request.put;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Certificate;
import com.weiver.applicant.dto.request.post.CertificateDetailDTO;
import jakarta.validation.Valid;

import java.util.List;

public record CertificateUpdateRequestDTO(
    @JsonProperty("CertificateUpdateDTO")
    @Valid
    List<CertificateUpdateDetailDTO> certificateList){
}

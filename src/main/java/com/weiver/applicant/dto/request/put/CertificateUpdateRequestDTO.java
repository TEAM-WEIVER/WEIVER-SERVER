package com.weiver.applicant.dto.request.put;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.List;

public record CertificateUpdateRequestDTO(
    @JsonProperty("CertificateUpdateDTO")
    @Valid
    List<CertificateUpdateDetailDTO> certificateList){
}

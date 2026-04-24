package com.weiver.applicant.dto.request.put;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CertificateUpdateRequestDTO(
    @Valid @NotNull
    @JsonProperty("CertificateUpdateDTO")
    List<CertificateUpdateDetailDTO> certificateList){
}

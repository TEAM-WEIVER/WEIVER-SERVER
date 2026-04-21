package com.weiver.applicant.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;

import java.util.List;

public record CertificateRequestDTO (
    @JsonProperty("CertificateDTO")
    @Valid
    List<CertificateDetailDTO> certificateList){}

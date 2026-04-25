package com.weiver.applicant.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record ApplicantInfoResponseDTO (
        @JsonProperty("ApplicantDTO") ApplicantDetailResponseDTO applicant,
        @JsonProperty("EducationDTO") List<EducationDetailResponseDTO> education,
        @JsonProperty("AwardDTO") List<AwardDetailResponseDTO> award,
        @JsonProperty("WorkExperienceDTO") List<WorkExperienceDetailResponseDTO> workExperience,
        @JsonProperty("CertificateDTO") List<CertificateDetailResponseDTO> certificate
){}

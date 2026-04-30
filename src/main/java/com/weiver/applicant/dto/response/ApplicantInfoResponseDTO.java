package com.weiver.applicant.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "구직자 전체 정보 응답 DTO (마이페이지 조회용 최상위 객체)")
public record ApplicantInfoResponseDTO (

        @Schema(description = "구직자 기본 정보 객체")
        @JsonProperty("ApplicantDTO")
        ApplicantDetailResponseDTO applicant,

        @Schema(description = "학력 정보 목록")
        @JsonProperty("EducationDTO")
        List<EducationDetailResponseDTO> education,

        @Schema(description = "수상 이력 목록")
        @JsonProperty("AwardDTO")
        List<AwardDetailResponseDTO> award,

        @Schema(description = "경력 정보 목록")
        @JsonProperty("WorkExperienceDTO")
        List<WorkExperienceDetailResponseDTO> workExperience,

        @Schema(description = "자격증 정보 목록")
        @JsonProperty("CertificateDTO")
        List<CertificateDetailResponseDTO> certificate
){}
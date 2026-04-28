package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Certificate;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "자격증 상세 응답 DTO")
public record CertificateDetailResponseDTO(

        @Schema(description = "자격증 ID", example = "1")
        Long certificateId,

        @Schema(description = "자격증 명칭", example = "정보처리기사")
        String certificateName,

        @Schema(description = "취득 날짜 (YYYY-MM-DD 형식)", example = "2025-03-01")
        String acquisitionDate,

        @Schema(description = "발급 기관", example = "한국산업인력공단")
        String issuer
) {
    public static CertificateDetailResponseDTO from(Certificate certificate) {
        return new CertificateDetailResponseDTO(
                certificate.getCertificateId(),
                certificate.getCertificateName(),
                certificate.getAcquisitionDate() != null ? certificate.getAcquisitionDate().toString() : null,
                certificate.getIssuer()
        );
    }
}
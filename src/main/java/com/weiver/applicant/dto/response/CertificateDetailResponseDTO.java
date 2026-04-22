package com.weiver.applicant.dto.response;

import com.weiver.applicant.domain.Certificate;

public record CertificateDetailResponseDTO(
        long certificateId,
        String certificateName,
        String acquisitionDate,
        String issuer
) {
    public static CertificateDetailResponseDTO from(Certificate certificate) {
        return new CertificateDetailResponseDTO(
                certificate.getCertificateId(),
                certificate.getCertificateName(),
                certificate.getAcquisitionDate().toString(),
                certificate.getIssuer()
        );
    }
}

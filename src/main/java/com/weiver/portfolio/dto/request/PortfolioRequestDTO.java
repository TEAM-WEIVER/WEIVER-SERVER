package com.weiver.portfolio.dto.request;

import com.weiver.applicant.domain.Applicant;
import com.weiver.portfolio.domain.Portfolio;

public record PortfolioRequestDTO(
        String fileKey,
        String fileName,
        String fileType,
        String urlGithub,
        String urlTech,
        String urlEtc
) {
    public Portfolio toEntity(Applicant applicant, Long fileSize){
        return Portfolio.builder()
                .fileKey(this.fileKey)
                .fileName(this.fileName)
                .fileType(this.fileType)
                .fileSize(fileSize)
                .urlGithub(this.urlGithub)
                .urlTech(this.urlTech)
                .urlEtc(this.urlEtc)
                .applicant(applicant)
                .build();
    }
}

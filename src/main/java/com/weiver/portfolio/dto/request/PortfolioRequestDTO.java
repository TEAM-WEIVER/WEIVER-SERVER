package com.weiver.portfolio.dto.request;

import com.weiver.applicant.domain.Applicant;
import com.weiver.portfolio.domain.Portfolio;
import org.hibernate.validator.constraints.URL;

public record PortfolioRequestDTO(
        String fileKey,
        String fileName,
        String fileType,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlGithub,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlTech,
        @URL(message = "올바른 URL 형식이 아닙니다.")
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

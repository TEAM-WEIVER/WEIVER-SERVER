package com.weiver.portfolio.dto.request;

import com.weiver.applicant.domain.Applicant;
import com.weiver.portfolio.domain.Portfolio;
import org.hibernate.validator.constraints.URL;
import org.springframework.web.multipart.MultipartFile;

public record PortfolioRequestDTO(
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlGithub,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlTech,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlEtc
) {
    public Portfolio toEntity(Applicant applicant, Long fileSize,
                              String fileName, String fileType, String fileKey){
        return Portfolio.builder()
                .fileName(fileName)
                .fileType(fileType)
                .fileSize(fileSize)
                .fileKey(fileKey)
                .urlGithub(this.urlGithub)
                .urlTech(this.urlTech)
                .urlEtc(this.urlEtc)
                .applicant(applicant)
                .build();
    }
}

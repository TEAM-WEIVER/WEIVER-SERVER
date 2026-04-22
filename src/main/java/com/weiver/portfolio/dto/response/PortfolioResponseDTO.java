package com.weiver.portfolio.dto.response;


import com.weiver.portfolio.domain.Portfolio;

public record PortfolioResponseDTO(
        long portfolioId,
        String fileKey,
        String fileName,
        String fileType,
        Long fileSize,
        String urlGithub,
        String urlTech,
        String urlEtc
) {
    public static PortfolioResponseDTO from(Portfolio portfolio) {
        return new PortfolioResponseDTO(
                portfolio.getPortfolioId(),
                portfolio.getFileKey(),
                portfolio.getFileName(),
                portfolio.getFileType(),
                portfolio.getFileSize(),
                portfolio.getUrlGithub(),
                portfolio.getUrlTech(),
                portfolio.getUrlEtc()
        );
    }
}

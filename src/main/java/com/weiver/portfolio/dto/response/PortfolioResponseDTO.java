package com.weiver.portfolio.dto.response;


import com.weiver.portfolio.domain.Portfolio;

public record PortfolioResponseDTO(
        Long portfolioId,
        String downloadUrl,
        String fileName,
        String fileType,
        Long fileSize,
        String urlGithub,
        String urlTech,
        String urlEtc
) {
    public static PortfolioResponseDTO from(Portfolio portfolio, String downloadUrl) {
        return new PortfolioResponseDTO(
                portfolio.getPortfolioId(),
                downloadUrl,
                portfolio.getFileName(),
                portfolio.getFileType(),
                portfolio.getFileSize(),
                portfolio.getUrlGithub(),
                portfolio.getUrlTech(),
                portfolio.getUrlEtc()
        );
    }
}

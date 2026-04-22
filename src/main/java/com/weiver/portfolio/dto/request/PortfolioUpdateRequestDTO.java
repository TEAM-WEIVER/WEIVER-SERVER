package com.weiver.portfolio.dto.request;

public record PortfolioUpdateRequestDTO(
        long portfolioId,
        String fileKey,
        String fileName,
        String fileType,
        String urlGithub,
        String urlTech,
        String urlEtc
) {}

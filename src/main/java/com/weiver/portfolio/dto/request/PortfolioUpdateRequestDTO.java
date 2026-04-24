package com.weiver.portfolio.dto.request;

import org.hibernate.validator.constraints.URL;

public record PortfolioUpdateRequestDTO(
        Long portfolioId,
        String fileKey,
        String fileName,
        String fileType,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlGithub,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlTech,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlEtc
) {}

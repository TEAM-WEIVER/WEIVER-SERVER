package com.weiver.portfolio.dto.request;

import org.hibernate.validator.constraints.URL;
import org.springframework.web.multipart.MultipartFile;

public record PortfolioUpdateRequestDTO(
        Long portfolioId,
        MultipartFile file,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlGithub,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlTech,
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlEtc
) {}

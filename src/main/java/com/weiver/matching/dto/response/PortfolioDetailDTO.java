package com.weiver.matching.dto.response;

import com.weiver.portfolio.domain.Portfolio;

public record PortfolioDetailDTO(
        String portfolioFileUrl,   // 포트폴리오 파일 다운로드 S3 URL (없을 경우 null)
        String urlGithub,          // 깃허브 링크
        String urlTech,          // 노션 링크
        String urlEtc       // 기타 개인사이트 링크
) {
    public static PortfolioDetailDTO of(Portfolio portfolio, String portfolioFileUrl) {
        return new PortfolioDetailDTO(
                portfolioFileUrl,
                portfolio.getUrlGithub(),
                portfolio.getUrlTech(),
                portfolio.getUrlEtc()
        );
    }
}

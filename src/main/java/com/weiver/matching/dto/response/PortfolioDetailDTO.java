package com.weiver.matching.dto.response;

import com.weiver.portfolio.domain.Portfolio;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "지원자 포트폴리오 상세 DTO")
public record PortfolioDetailDTO(
        @Schema(description = "포트폴리오 파일 다운로드 URL입니다. 등록된 파일이 없으면 null입니다.", example = "https://weiver-bucket.s3.ap-northeast-2.amazonaws.com/portfolio/applicant.pdf", nullable = true)
        String portfolioFileUrl,

        @Schema(description = "지원자의 GitHub 프로필 또는 저장소 URL입니다.", example = "https://github.com/weiver-dev", nullable = true)
        String urlGithub,

        @Schema(description = "지원자의 기술 블로그 또는 기술 문서 URL입니다.", example = "https://velog.io/@weiver", nullable = true)
        String urlTech,

        @Schema(description = "기타 포트폴리오 URL입니다. 노션, 개인 웹사이트 등이 포함될 수 있습니다.", example = "https://notion.so/weiver-portfolio", nullable = true)
        String urlEtc
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

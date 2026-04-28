package com.weiver.portfolio.dto.response;

import com.weiver.portfolio.domain.Portfolio;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "포트폴리오 응답 DTO")
public record PortfolioResponseDTO(

        @Schema(description = "포트폴리오 ID", example = "1")
        Long portfolioId,

        @Schema(description = "포트폴리오 파일 다운로드 임시 URL (S3 Presigned URL, 발급 후 30분간 유효)",
                example = "https://weiver-private-bucket.s3.ap-northeast-2.amazonaws.com/portfolios/uuid.pdf?X-Amz-Algorithm=...",
                nullable = true)
        String downloadUrl,

        @Schema(description = "업로드한 원본 파일명", example = "이현우_서버개발자_포트폴리오.pdf", nullable = true)
        String fileName,

        @Schema(description = "파일 MIME 타입", example = "application/pdf", nullable = true)
        String fileType,

        @Schema(description = "파일 크기 (Byte 단위)", example = "1048576", nullable = true)
        Long fileSize,

        @Schema(description = "GitHub 프로필 또는 레포지토리 URL", example = "https://github.com/weiver-dev", nullable = true)
        String urlGithub,

        @Schema(description = "기술 블로그 URL", example = "https://velog.io/@weiver", nullable = true)
        String urlTech,

        @Schema(description = "기타 포트폴리오 링크 (노션 등)", example = "https://notion.so/weiver-portfolio", nullable = true)
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
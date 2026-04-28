package com.weiver.portfolio.dto.request;

import com.weiver.applicant.domain.Applicant;
import com.weiver.portfolio.domain.Portfolio;
import io.swagger.v3.oas.annotations.media.Schema;
import org.hibernate.validator.constraints.URL;
import org.springframework.web.multipart.MultipartFile;

@Schema(description = "포트폴리오 텍스트 정보 저장 요청 DTO (파일 제외)")
public record PortfolioRequestDTO(
        @Schema(description = "GitHub 프로필 또는 레포지토리 URL", example = "https://github.com/weiver-dev", nullable = true)
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlGithub,

        @Schema(description = "기술 블로그 (Velog, Tistory 등) URL", example = "https://velog.io/@weiver", nullable = true)
        @URL(message = "올바른 URL 형식이 아닙니다.")
        String urlTech,

        @Schema(description = "기타 포트폴리오 URL (노션 이력서 등)", example = "https://notion.so/weiver-portfolio", nullable = true)
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

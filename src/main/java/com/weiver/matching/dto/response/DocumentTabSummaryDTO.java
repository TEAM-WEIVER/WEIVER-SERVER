package com.weiver.matching.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "지원자 제출 서류 및 면접 스크립트 요약 응답 DTO")
public record DocumentTabSummaryDTO(
        @Schema(description = "지원자의 포트폴리오 파일 및 외부 링크 정보입니다. 등록된 포트폴리오가 없으면 내부 필드가 null로 반환됩니다.")
        @JsonProperty("portfolio")
        PortfolioDetailDTO portfolioDetailDTO,

        @Schema(description = "기술면접 스크립트 질문/답변 목록입니다.")
        List<InterviewScriptDTO> techInterviewScripts,

        @Schema(description = "인성 또는 컬처핏 면접 스크립트 질문/답변 목록입니다.")
        List<InterviewScriptDTO> cultureInterviewScripts
) {
    public static DocumentTabSummaryDTO of(
            PortfolioDetailDTO portfolioDetailDTO,
            List<InterviewScriptDTO> techInterviewScripts,
            List<InterviewScriptDTO> cultureInterviewScripts
    ) {
        return new DocumentTabSummaryDTO(
                portfolioDetailDTO,
                techInterviewScripts,
                cultureInterviewScripts
        );
    }
}

package com.weiver.matching.dto.response;

import java.util.List;

public record DocumentTabSummaryDTO(
        PortfolioDetailDTO portfolioDetailDTO,
        List<InterviewScriptDTO> techInterviewScripts,   // 기술면접 스크립트 (Q&A 리스트)
        List<InterviewScriptDTO> cultureInterviewScripts  // 인성(컬처핏)면접 스크립트 (Q&A 리스트)
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

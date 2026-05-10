package com.weiver.matching.dto.response;

import com.weiver.analysis.dto.response.CompetencyDetailDTO;

import java.util.List;

public record SkillFitSummaryDTO(
        List<CompetencyDetailDTO> aiSkillAnalysis,  // AI 역량 분석
        String aiAbilitySummary,   // AI 역량 평가
        List<String> skillTags,           // 보유 스킬 키워드
        Float matchingRate          // 매칭률
) {
    public static SkillFitSummaryDTO of(List<CompetencyDetailDTO> aiSkillAnalysis, String aiAbilitySummary,
                                        List<String> skillTags, Float matchingRate) {
        return new SkillFitSummaryDTO(
                aiSkillAnalysis,
                aiAbilitySummary,
                skillTags,
                matchingRate
        );
    }
}

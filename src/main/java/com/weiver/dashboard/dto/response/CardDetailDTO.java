package com.weiver.dashboard.dto.response;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.matching.domain.MatchResult;

import java.util.Arrays;
import java.util.List;

import static java.lang.String.valueOf;

public record CardDetailDTO (
        Integer skillScore,
        String note,
        String culturefitStyle, // 구직자 컬처 리포트
        List<String> skillTags // 구직자 기술 리포트 (JSONB)
){
    public static CardDetailDTO of(MatchResult matchResult, CultureReport cultureReport, TechnicalSkillReport technicalSkillReport) {
        return new CardDetailDTO(
                matchResult.getSkillScore() != null ? Math.round(matchResult.getSkillScore()) : null,
                matchResult.getNote(),
                cultureReport != null ? valueOf(cultureReport.getCulturefitStyles()) : null,
                technicalSkillReport != null ? parseJsonToList(valueOf(technicalSkillReport.getSkillTags())) : null
        );
    }
    
    private static List<String> parseJsonToList(String jsonString) {
        if (jsonString == null || jsonString.isBlank()) return List.of();
        return Arrays.asList(jsonString.replace("[\"", "").replace("\"]", "").split("\",\""));
    }
}

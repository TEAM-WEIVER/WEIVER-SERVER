package com.weiver.matching.dto.response;

import com.weiver.analysis.dto.response.CompetencyDetailDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "지원자 스킬핏 요약 응답 DTO")
public record SkillFitSummaryDTO(
        @Schema(description = "AI가 평가한 역량별 상세 점수 목록입니다. 각 점수는 0부터 100까지의 퍼센트 정수입니다.")
        List<CompetencyDetailDTO> aiSkillAnalysis,

        @Schema(description = "채용 공고의 역량 우선순위와 지원자의 역량 분석 결과를 비교해 생성한 AI 평가 문장입니다.", example = "우선순위 역량 중 1순위, 2순위 역량이 문제해결력 88%, 협업 및 커뮤니케이션 84%로 일치합니다.")
        String aiAbilitySummary,

        @Schema(description = "지원자의 보유 기술 태그 목록입니다.", example = "[\"Java\", \"Spring Boot\", \"JPA\", \"PostgreSQL\"]")
        List<String> skillTags,

        @Schema(description = "채용 공고와 지원자 간 전체 매칭률입니다. 0부터 100까지의 점수이며 소수점이 포함될 수 있습니다.", example = "87.5")
        Float matchingRate
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

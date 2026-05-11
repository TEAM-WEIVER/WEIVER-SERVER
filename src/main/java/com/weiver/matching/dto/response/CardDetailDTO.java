package com.weiver.matching.dto.response;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.matching.domain.MatchResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "지원자 카드 상세 정보 DTO")
public record CardDetailDTO(
        @Schema(description = "지원자의 스킬핏 점수입니다. 원본 소수점 점수를 반올림한 정수 값이며, 점수가 없으면 null입니다.", example = "86", nullable = true)
        Integer skillScore,

        @Schema(description = "기업 담당자가 매칭 결과에 남긴 내부 메모입니다.", example = "Spring Boot 실무 경험이 충분하고 협업 경험도 확인되었습니다.", nullable = true)
        String note,

        @Schema(description = "AI 컬처핏 분석에서 도출된 지원자의 대표 조직문화 성향입니다.", example = "추진형 실행가", nullable = true)
        String culturefitStyle,

        @Schema(description = "AI 기술 리포트에서 추출된 지원자의 보유 기술 태그 목록입니다.", example = "[\"Java\", \"Spring Boot\", \"MySQL\", \"Redis\"]", nullable = true)
        List<String> skillTags
) {
    public static CardDetailDTO of(MatchResult matchResult, CultureReport cultureReport, TechnicalSkillReport technicalSkillReport) {
        return new CardDetailDTO(
                matchResult.getSkillScore() != null ? Math.round(matchResult.getSkillScore()) : null,
                matchResult.getNote(),
                cultureReport != null ? cultureReport.getCulturefitStyles().getDescription() : null,
                technicalSkillReport != null ? technicalSkillReport.getSkillTags() : null
        );
    }
}

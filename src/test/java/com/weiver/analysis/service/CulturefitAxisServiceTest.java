package com.weiver.analysis.service;

import com.weiver.analysis.type.CultureAxis;
import com.weiver.analysis.type.CulturefitStyle;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class CulturefitAxisServiceTest {

    private final CulturefitAxisService culturefitAxisService = new CulturefitAxisService();

    private static final Map<String, Double> EXTRACTED_CULTUREFIT = Map.of(
            "자기방향", 0.72,
            "자극", 0.55,
            "쾌락", 0.31,
            "성취", 0.84,
            "권력", 0.22,
            "안전", 0.61,
            "순응", 0.48,
            "전통", 0.35,
            "호의", 0.76,
            "보편주의", 0.69
    );

    @Test
    @DisplayName("축별 점수는 축에 속한 가치 원점수의 평균이다(쾌락은 자율·혁신 그룹)")
    void calculateAxisScores_AveragesRawScoresPerAxis() {
        Map<String, Double> scores = culturefitAxisService.calculateAxisScores(EXTRACTED_CULTUREFIT);

        assertThat(scores).containsOnlyKeys(
                CultureAxis.OPENNESS_TO_CHANGE.getKey(),
                CultureAxis.SELF_ENHANCEMENT.getKey(),
                CultureAxis.CONSERVATION.getKey(),
                CultureAxis.SELF_TRANSCENDENCE.getKey());
        assertThat(scores.get("openness_to_change")).isCloseTo((0.72 + 0.55 + 0.31) / 3, within(1e-9));
        assertThat(scores.get("self_enhancement")).isCloseTo((0.84 + 0.22) / 2, within(1e-9));
        assertThat(scores.get("conservation")).isCloseTo((0.61 + 0.48 + 0.35) / 3, within(1e-9));
        assertThat(scores.get("self_transcendence")).isCloseTo((0.76 + 0.69) / 2, within(1e-9));
    }

    @Test
    @DisplayName("좌표는 10개 점수를 정규화한 뒤 x = 자율·혁신 - 안정·질서, y = 성과·영향 - 관계·공동체로 계산한다")
    void calculateAxis_NormalizesThenSubtractsOpposingAxes() {
        double mean = EXTRACTED_CULTUREFIT.values().stream().mapToDouble(Double::doubleValue).sum() / 10;
        double openness = (0.72 + 0.55 + 0.31) / 3 - mean;
        double enhancement = (0.84 + 0.22) / 2 - mean;
        double conservation = (0.61 + 0.48 + 0.35) / 3 - mean;
        double transcendence = (0.76 + 0.69) / 2 - mean;

        CulturefitAxisService.AxisPoint point = culturefitAxisService.calculateAxis(EXTRACTED_CULTUREFIT);

        assertThat(point.x()).isCloseTo(openness - conservation, within(1e-9));
        assertThat(point.y()).isCloseTo(enhancement - transcendence, within(1e-9));
    }

    @Test
    @DisplayName("점수가 비어 있으면 축 점수와 좌표 모두 0이다")
    void calculateAxis_HandlesEmptyScores() {
        assertThat(culturefitAxisService.calculateAxisScores(Map.of()).values()).allMatch(value -> value == 0.0);
        assertThat(culturefitAxisService.calculateAxis(Map.of()).x()).isZero();
        assertThat(culturefitAxisService.calculateAxis(Map.of()).y()).isZero();
    }

    @Test
    @DisplayName("일부 가치가 누락돼도 존재하는 값만으로 축 평균을 구한다")
    void calculateAxisScores_IgnoresMissingTraits() {
        Map<String, Double> partial = new HashMap<>();
        partial.put("자기방향", 0.8);
        partial.put("성취", 0.6);

        Map<String, Double> scores = culturefitAxisService.calculateAxisScores(partial);

        assertThat(scores.get("openness_to_change")).isCloseTo(0.8, within(1e-9));
        assertThat(scores.get("self_enhancement")).isCloseTo(0.6, within(1e-9));
        assertThat(scores.get("conservation")).isZero();
        assertThat(scores.get("self_transcendence")).isZero();
    }

    @Test
    @DisplayName("축 그룹 정의는 CultureAxis 하나에서만 오고, 10개 가치를 중복 없이 모두 덮는다")
    void axisGrouping_ComesFromSharedEnumAndCoversAllTraits() {
        List<String> allTraits = Arrays.stream(CultureAxis.values())
                .flatMap(axis -> axis.getTraits().stream())
                .toList();

        assertThat(allTraits).doesNotHaveDuplicates();
        assertThat(allTraits).containsExactlyInAnyOrderElementsOf(EXTRACTED_CULTUREFIT.keySet());
        // 쾌락은 성과·영향이 아니라 자율·혁신 그룹이다.
        assertThat(CultureAxis.OPENNESS_TO_CHANGE.getTraits()).contains("쾌락");
        assertThat(CultureAxis.SELF_ENHANCEMENT.getTraits()).doesNotContain("쾌락");
    }

    @Test
    @DisplayName("사분면별로 컬처핏 스타일을 판정한다")
    void resolveStyle_MapsQuadrantToStyle() {
        assertThat(culturefitAxisService.resolveStyle(new CulturefitAxisService.AxisPoint(0.12, 0.05)))
                .isEqualTo(CulturefitStyle.AGGRESSIVE_INNOVATOR);
        assertThat(culturefitAxisService.resolveStyle(new CulturefitAxisService.AxisPoint(0.12, -0.05)))
                .isEqualTo(CulturefitStyle.INCLUSIVE_INNOVATOR);
        assertThat(culturefitAxisService.resolveStyle(new CulturefitAxisService.AxisPoint(-0.12, 0.05)))
                .isEqualTo(CulturefitStyle.STRATEGIC_GUARDIAN);
        assertThat(culturefitAxisService.resolveStyle(new CulturefitAxisService.AxisPoint(-0.12, -0.05)))
                .isEqualTo(CulturefitStyle.STEADY_SUPPORTER);
    }

    @Test
    @DisplayName("경계: 좌표가 원점이면 공격적 혁신가로 판정한다")
    void resolveStyle_TreatsOriginAsAggressiveInnovator() {
        assertThat(culturefitAxisService.resolveStyle(new CulturefitAxisService.AxisPoint(0.0, 0.0)))
                .isEqualTo(CulturefitStyle.AGGRESSIVE_INNOVATOR);
    }
}

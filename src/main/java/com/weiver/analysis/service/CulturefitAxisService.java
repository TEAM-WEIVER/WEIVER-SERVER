package com.weiver.analysis.service;

import com.weiver.analysis.type.CulturefitStyle;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Schwartz 10개 기본 가치 점수를 상위 4개 문화 축과 2차원 좌표로 환산한다.
 *
 * <p>AI 서버가 {@code culture_axis}로 보내는 좌표와 같은 방식으로 계산한다.
 * <ol>
 *   <li>10개 점수의 평균을 구해 각 점수에서 평균을 뺀다(정규화).</li>
 *   <li>정규화된 점수를 4개 축 그룹으로 묶어 그룹별 평균을 구한다.</li>
 *   <li>x = 자율·혁신 − 안정·질서, y = 성과·영향 − 관계·공동체.</li>
 * </ol>
 *
 * <p>화면의 4축 게이지는 0~100 퍼센트이므로, 축별 퍼센트는 정규화 값(음수 가능)이 아니라
 * <b>원점수(0~1)의 그룹 평균</b>을 쓴다({@link #calculateAxisScores}). 정규화·사분면은 좌표 전용이다.
 */
@Service
public class CulturefitAxisService {

    /**
     * 컬처핏 2차원 좌표. x = 자율·혁신 − 안정·질서, y = 성과·영향 − 관계·공동체.
     */
    public record AxisPoint(double x, double y) {
    }

    /** 자율·혁신 (Openness to change) */
    private static final String OPENNESS_TO_CHANGE = "openness_to_change";
    /** 성과·영향 (Self-enhancement) */
    private static final String SELF_ENHANCEMENT = "self_enhancement";
    /** 안정·질서 (Conservation) */
    private static final String CONSERVATION = "conservation";
    /** 관계·공동체 (Self-transcendence) */
    private static final String SELF_TRANSCENDENCE = "self_transcendence";

    /** 4개 상위 축 → 소속 Schwartz 가치(AI payload의 extracted_culturefit 키). */
    private static final Map<String, List<String>> AXIS_TRAITS = Map.of(
            OPENNESS_TO_CHANGE, List.of("자기방향", "자극", "쾌락"),
            SELF_ENHANCEMENT, List.of("성취", "권력"),
            CONSERVATION, List.of("안전", "순응", "전통"),
            SELF_TRANSCENDENCE, List.of("호의", "보편주의")
    );

    /** 축 순서를 응답·저장에서 고정하기 위한 목록. */
    private static final List<String> AXIS_ORDER =
            List.of(OPENNESS_TO_CHANGE, SELF_ENHANCEMENT, CONSERVATION, SELF_TRANSCENDENCE);

    /**
     * 축별 원점수 평균(0~1). 기업 리포트의 4축 퍼센트 게이지가 이 값을 100배해 쓴다.
     */
    public Map<String, Double> calculateAxisScores(Map<String, Double> extractedCulturefit) {
        return groupAverages(extractedCulturefit, 0.0);
    }

    /**
     * 10개 점수를 정규화(평균 차감)한 뒤 축 그룹 평균을 구해 2차원 좌표를 계산한다.
     * AI 서버가 좌표를 보내지 않았을 때의 대체 계산용이다.
     */
    public AxisPoint calculateAxis(Map<String, Double> extractedCulturefit) {
        double mean = mean(extractedCulturefit);
        Map<String, Double> normalized = groupAverages(extractedCulturefit, mean);

        return new AxisPoint(
                normalized.get(OPENNESS_TO_CHANGE) - normalized.get(CONSERVATION),
                normalized.get(SELF_ENHANCEMENT) - normalized.get(SELF_TRANSCENDENCE)
        );
    }

    /**
     * 좌표가 속한 사분면으로 컬처핏 스타일을 판정한다.
     *
     * <pre>
     *   x ≥ 0 (자율·혁신) · y ≥ 0 (성과·영향)  → 공격적 혁신가
     *   x ≥ 0 (자율·혁신) · y &lt; 0 (관계·공동체) → 포용적 혁신가
     *   x &lt; 0 (안정·질서) · y ≥ 0 (성과·영향)  → 전략적 수호자
     *   x &lt; 0 (안정·질서) · y &lt; 0 (관계·공동체) → 안정적 조력가
     * </pre>
     */
    public CulturefitStyle resolveStyle(AxisPoint cultureAxis) {
        double x = cultureAxis.x();
        double y = cultureAxis.y();

        if (x >= 0) {
            return y >= 0 ? CulturefitStyle.AGGRESSIVE_INNOVATOR : CulturefitStyle.INCLUSIVE_INNOVATOR;
        }
        return y >= 0 ? CulturefitStyle.STRATEGIC_GUARDIAN : CulturefitStyle.STEADY_SUPPORTER;
    }

    /**
     * 축별로 소속 가치 점수에서 {@code offset}을 뺀 값의 평균을 구한다.
     * offset이 0이면 원점수 평균, 전체 평균이면 정규화 평균이 된다.
     */
    private Map<String, Double> groupAverages(Map<String, Double> extractedCulturefit, double offset) {
        Map<String, Double> result = new LinkedHashMap<>();

        for (String axis : AXIS_ORDER) {
            double sum = 0;
            int count = 0;
            for (String trait : AXIS_TRAITS.get(axis)) {
                Double score = value(extractedCulturefit, trait);
                if (score != null) {
                    sum += score - offset;
                    count++;
                }
            }
            result.put(axis, count > 0 ? sum / count : 0.0);
        }
        return result;
    }

    private double mean(Map<String, Double> extractedCulturefit) {
        double sum = 0;
        int count = 0;
        for (String axis : AXIS_ORDER) {
            for (String trait : AXIS_TRAITS.get(axis)) {
                Double score = value(extractedCulturefit, trait);
                if (score != null) {
                    sum += score;
                    count++;
                }
            }
        }
        return count > 0 ? sum / count : 0.0;
    }

    private Double value(Map<String, Double> extractedCulturefit, String trait) {
        return extractedCulturefit != null ? extractedCulturefit.get(trait) : null;
    }

}

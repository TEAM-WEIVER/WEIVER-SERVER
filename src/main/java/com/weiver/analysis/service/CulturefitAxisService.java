package com.weiver.analysis.service;

import com.weiver.analysis.type.CultureAxis;
import com.weiver.analysis.type.CulturefitStyle;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
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
 *
 * <p>축→가치 매핑은 {@link CultureAxis}가 단일 출처다. 표시 로직도 같은 enum을 써야 게이지 값과
 * 하위 분해가 어긋나지 않는다.
 */
@Service
public class CulturefitAxisService {

    /**
     * 컬처핏 2차원 좌표. x = 자율·혁신 − 안정·질서, y = 성과·영향 − 관계·공동체.
     */
    public record AxisPoint(double x, double y) {
    }

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
        Map<String, Double> normalized = groupAverages(extractedCulturefit, mean(extractedCulturefit));

        return new AxisPoint(
                normalized.get(CultureAxis.OPENNESS_TO_CHANGE.getKey())
                        - normalized.get(CultureAxis.CONSERVATION.getKey()),
                normalized.get(CultureAxis.SELF_ENHANCEMENT.getKey())
                        - normalized.get(CultureAxis.SELF_TRANSCENDENCE.getKey())
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
        if (cultureAxis.x() >= 0) {
            return cultureAxis.y() >= 0
                    ? CulturefitStyle.AGGRESSIVE_INNOVATOR
                    : CulturefitStyle.INCLUSIVE_INNOVATOR;
        }
        return cultureAxis.y() >= 0
                ? CulturefitStyle.STRATEGIC_GUARDIAN
                : CulturefitStyle.STEADY_SUPPORTER;
    }

    /**
     * 축별로 소속 가치 점수에서 {@code offset}을 뺀 값의 평균을 구한다.
     * offset이 0이면 원점수 평균, 전체 평균이면 정규화 평균이 된다.
     */
    private Map<String, Double> groupAverages(Map<String, Double> extractedCulturefit, double offset) {
        Map<String, Double> result = new LinkedHashMap<>();

        for (CultureAxis axis : CultureAxis.values()) {
            double sum = 0;
            int count = 0;
            for (String trait : axis.getTraits()) {
                Double score = value(extractedCulturefit, trait);
                if (score != null) {
                    sum += score - offset;
                    count++;
                }
            }
            result.put(axis.getKey(), count > 0 ? sum / count : 0.0);
        }
        return result;
    }

    private double mean(Map<String, Double> extractedCulturefit) {
        double sum = 0;
        int count = 0;
        for (CultureAxis axis : CultureAxis.values()) {
            for (String trait : axis.getTraits()) {
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

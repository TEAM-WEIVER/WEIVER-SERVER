package com.weiver.analysis.dto.response;

import java.util.List;

public record CultureFitSummaryDTO(
        String matchStatus,
        String culturefitStyle,
        List<CultureAxisDTO> topTwoAxes, // 상단에 노출될 가장 높은 점수의 축 2개
        String aiSummary,
        List<AxisDetailDTO> axesDetails // 하단 4개 축 상세 데이터 (자율·혁신, 성과·영향 등)
) {
    public static CultureFitSummaryDTO of(String matchStatus, String culturefitStyle,
                                          List<CultureAxisDTO> topTwoAxes, String aiSummary,
                                          List<AxisDetailDTO> axesDetails) {
        return new CultureFitSummaryDTO(matchStatus, culturefitStyle, topTwoAxes, aiSummary, axesDetails);
    }
}


package com.weiver.analysis.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "지원자 컬처핏 요약 응답 DTO")
public record CultureFitSummaryDTO(
        @Schema(description = "채용 공고와 지원자 간 컬처핏 적합 상태입니다. 매칭률 기준으로 화면에서 강조 문구로 사용할 수 있습니다.", example = "높은 매칭률")
        String matchStatus,

        @Schema(description = "AI 컬처핏 리포트에서 도출한 지원자의 대표 조직문화 성향입니다.", example = "추진형 실행가")
        String culturefitStyle,

        @Schema(description = "4개 문화 성향 축 중 점수가 가장 높은 상위 2개 축입니다. 컬처핏 요약 차트의 핵심 지표로 사용됩니다.")
        List<CultureAxisDTO> topTwoAxes,

        @Schema(description = "지원자의 컬처핏 특성과 채용 공고 적합성을 설명하는 AI 요약 문장입니다.", example = "지원자는 빠른 실행과 명확한 목표 지향성이 강하며, 자율적으로 문제를 해결하는 조직문화와 잘 맞습니다.")
        String aiSummary,

        @Schema(description = "자율·혁신, 성과·영향, 안정·질서, 관계·공동체 4개 문화 축의 상세 점수와 하위 성향 목록입니다.")
        List<AxisDetailDTO> axesDetails
) {
    public static CultureFitSummaryDTO of(String matchStatus, String culturefitStyle,
                                          List<CultureAxisDTO> topTwoAxes, String aiSummary,
                                          List<AxisDetailDTO> axesDetails) {
        return new CultureFitSummaryDTO(matchStatus, culturefitStyle, topTwoAxes, aiSummary, axesDetails);
    }
}

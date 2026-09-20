package com.weiver.interview.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 최종 평가(INTERVIEW_REPORT_COMPLETED) 결과 이벤트 payload.
 *
 * <ul>
 *   <li>{@code evaluation} — 기술핏 평가. {@code criteria_summary}(역량별 요약)와 {@code overall_score}.</li>
 *   <li>{@code extracted_culturefit} — Schwartz 10개 가치별 점수(0~1). 키는 한글 가치명.</li>
 *   <li>{@code culture_axis} — 10개 점수를 정규화·그룹핑해 AI가 계산한 2차원 좌표({@code x_axis}, {@code y_axis}).</li>
 * </ul>
 */
public record InterviewReportCompletedData(
        @JsonProperty("applicant_id")
        Long applicantId,

        @JsonProperty("interview_session_id")
        UUID interviewSessionId,

        @JsonProperty("applicant_name")
        String applicantName,

        @JsonProperty("skill_tags")
        List<String> skillTags,

        @JsonProperty("user_provided_tags")
        List<String> userProvidedTags,

        Map<String, Object> evaluation,

        @JsonProperty("extracted_culturefit")
        Map<String, Double> extractedCulturefit,

        @JsonProperty("culture_axis")
        CultureAxisData cultureAxis
) {

    /**
     * 컬처핏 2차원 좌표. x = 자율·혁신 − 안정·질서, y = 성과·영향 − 관계·공동체.
     */
    public record CultureAxisData(
            @JsonProperty("x_axis")
            Double xAxis,

            @JsonProperty("y_axis")
            Double yAxis
    ) {
    }
}

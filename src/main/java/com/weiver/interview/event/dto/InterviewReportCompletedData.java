package com.weiver.interview.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI 최종 평가 결과 이벤트 payload.
 *
 * evaluation은 권장 형식인 nested map을 우선 지원한다.
 * - skill_analysis: 기술핏 상세 분석
 * - culture_analysis: 컬처핏 상세 분석
 *
 * 전환기 호환을 위해 criteria_summary 등 flat skill analysis map도 수용한다.
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

        Map<String, Object> evaluation
) {
}

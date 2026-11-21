package com.weiver.analysis.event.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 지원자 분석 완료(APPLICANT_ANALYSIS_COMPLETED) data. 성공과 실패를 모두 담는다.
 *
 * <p>AI 배포 전에는 {@code outcome}·{@code portfolio_status}·{@code portfolio_issue}가 없을 수 있어 모두 null을 허용한다.
 * {@code outcome}이 없으면 성공으로 본다. stage·code·portfolio 값은 Spring에서 검증하지 않고 원문 그대로 저장한다.
 */
public record ApplicantAnalysisCompletedData(
        @JsonProperty("applicant_id")
        Long applicantId,
        @JsonProperty("submission_version")
        Integer submissionVersion,
        /** SUCCEEDED 또는 FAILED. null이면 SUCCEEDED로 본다. */
        String outcome,
        @JsonProperty("skill_tags")
        List<String> skillTags,
        String job,
        String role,
        @JsonProperty("culturefit_style")
        String culturefitStyle,
        @JsonProperty("culturefit_tags")
        List<String> culturefitTags,
        /** pdf·none·unavailable (원문). */
        @JsonProperty("portfolio_status")
        String portfolioStatus,
        /** portfolio_status=unavailable일 때 파일 문제의 이유 (원문). */
        @JsonProperty("portfolio_issue")
        String portfolioIssue,
        /** 실패일 때만 온다. */
        ErrorData error
) {
    public static final String OUTCOME_SUCCEEDED = "SUCCEEDED";
    public static final String OUTCOME_FAILED = "FAILED";

    /** outcome이 FAILED인지. null(AI 배포 전)과 SUCCEEDED는 성공이다. */
    @JsonIgnore
    public boolean isFailed() {
        return OUTCOME_FAILED.equals(outcome);
    }

    /** outcome이 계약 값(SUCCEEDED·FAILED) 또는 null인지. */
    @JsonIgnore
    public boolean hasSupportedOutcome() {
        return outcome == null || OUTCOME_SUCCEEDED.equals(outcome) || OUTCOME_FAILED.equals(outcome);
    }

    /** 실패 정보. stage·code는 원문 그대로 저장한다. */
    public record ErrorData(
            String stage,
            String code
    ) {
    }
}

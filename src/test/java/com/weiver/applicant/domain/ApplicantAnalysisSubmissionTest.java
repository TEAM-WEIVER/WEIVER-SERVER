package com.weiver.applicant.domain;

import com.weiver.applicant.type.AnalysisFailureReason;
import com.weiver.applicant.type.AnalysisSubmissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ApplicantAnalysisSubmissionTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-22T10:00:00+09:00");

    @Test
    @DisplayName("요청 중인 제출 건을 완료하면 COMPLETED와 완료 시각, 포트폴리오 결과 원문이 저장된다")
    void markCompleted_FromRequested() {
        // given
        ApplicantAnalysisSubmission submission = requested();

        // when
        submission.markCompleted("unavailable", "encrypted", NOW);

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.COMPLETED);
        assertThat(submission.isCompleted()).isTrue();
        assertThat(submission.getCompletedAt()).isEqualTo(NOW);
        assertThat(submission.getPortfolioStatus()).isEqualTo("unavailable");
        assertThat(submission.getPortfolioIssue()).isEqualTo("encrypted");
    }

    @Test
    @DisplayName("요청 중인 제출 건이 분석 실패하면 FAILED·ANALYSIS_FAILED와 error 원문, 실패 시각이 저장된다")
    void markAnalysisFailed_FromRequested() {
        // given
        ApplicantAnalysisSubmission submission = requested();

        // when
        submission.markAnalysisFailed("EMBEDDING", "LLM_UNAVAILABLE", NOW);

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.FAILED);
        assertThat(submission.getFailureReason()).isEqualTo(AnalysisFailureReason.ANALYSIS_FAILED);
        assertThat(submission.getErrorStage()).isEqualTo("EMBEDDING");
        assertThat(submission.getErrorCode()).isEqualTo("LLM_UNAVAILABLE");
        assertThat(submission.getFailedAt()).isEqualTo(NOW);
        assertThat(submission.allowsResubmission()).isTrue();
    }

    @Test
    @DisplayName("REQUESTED가 아닌 제출 건은 다시 완료·실패로 바꿀 수 없다")
    void transitions_RequireRequested() {
        // given
        ApplicantAnalysisSubmission completed = requested();
        completed.markCompleted("pdf", null, NOW);
        ApplicantAnalysisSubmission failed = requested();
        failed.markAnalysisFailed("ANALYSIS", "INTERNAL", NOW);

        // when & then
        assertThatThrownBy(() -> completed.markAnalysisFailed("ANALYSIS", "INTERNAL", NOW))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> failed.markCompleted("pdf", null, NOW))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("요청 event_id와 같은 correlation_id만 이 제출 건의 결과로 본다")
    void isResultOf_ComparesEventId() {
        ApplicantAnalysisSubmission submission = requested();

        assertThat(submission.isResultOf("event-1")).isTrue();
        assertThat(submission.isResultOf("event-2")).isFalse();
        assertThat(submission.isResultOf(null)).isFalse();
    }

    private ApplicantAnalysisSubmission requested() {
        return ApplicantAnalysisSubmission.request(
                Applicant.builder().applicantId(1L).build(), 1, "event-1", Map.of(), "a".repeat(64),
                NOW.minusMinutes(5), Duration.ofMinutes(30));
    }
}

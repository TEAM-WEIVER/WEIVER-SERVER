package com.weiver.analysis.service;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.event.dto.ApplicantAnalysisCompletedData;
import com.weiver.analysis.repository.CultureReportRepository;
import com.weiver.analysis.repository.TechnicalSkillReportRepository;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAnalysisSubmission;
import com.weiver.applicant.repository.ApplicantAnalysisSubmissionRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.AnalysisFailureReason;
import com.weiver.applicant.type.AnalysisSubmissionStatus;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApplicantAnalysisResultServiceTest {

    private static final String REQUEST_EVENT_ID = "request-event-2";

    @Mock private ApplicantRepository applicantRepository;
    @Mock private ApplicantAnalysisSubmissionRepository submissionRepository;
    @Mock private TechnicalSkillReportRepository technicalSkillReportRepository;
    @Mock private CultureReportRepository cultureReportRepository;

    @InjectMocks
    private ApplicantAnalysisResultService resultService;

    @Test
    @DisplayName("현재 버전·correlation_id가 맞는 성공 결과는 리포트를 upsert하고 제출 건을 COMPLETED로 바꾼다")
    void applyCompletion_MatchingSuccess_CompletesAndUpsertsReports() {
        // given
        Applicant applicant = applicant(2);
        ApplicantAnalysisSubmission submission = requested(applicant, 2);
        givenLockedApplicantWith(applicant, submission);
        given(technicalSkillReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());
        given(cultureReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());

        // when
        resultService.applyCompletion(REQUEST_EVENT_ID, success(2, "pdf", null));

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.COMPLETED);
        assertThat(submission.getCompletedAt()).isNotNull();
        assertThat(submission.getPortfolioStatus()).isEqualTo("pdf");
        assertThat(submission.getPortfolioIssue()).isNull();
        assertThat(submission.getFailureReason()).isNull();

        ArgumentCaptor<TechnicalSkillReport> technical = ArgumentCaptor.forClass(TechnicalSkillReport.class);
        verify(technicalSkillReportRepository).save(technical.capture());
        assertThat(technical.getValue().getJob()).isEqualTo("DEVELOPER");
        assertThat(technical.getValue().getRole()).isEqualTo("BACKEND");
        assertThat(technical.getValue().getSkillTags()).containsExactly("Java");
        ArgumentCaptor<CultureReport> culture = ArgumentCaptor.forClass(CultureReport.class);
        verify(cultureReportRepository).save(culture.capture());
        assertThat(culture.getValue().getCulturefitStyles()).isEqualTo(CulturefitStyle.INCLUSIVE_INNOVATOR);
    }

    @Test
    @DisplayName("portfolio_status=unavailable과 portfolio_issue를 받으면 성공으로 처리하고 두 값을 저장한다")
    void applyCompletion_PortfolioUnavailable_StillCompletes() {
        // given
        Applicant applicant = applicant(2);
        ApplicantAnalysisSubmission submission = requested(applicant, 2);
        givenLockedApplicantWith(applicant, submission);
        TechnicalSkillReport existing = TechnicalSkillReport.builder().applicant(applicant).job("OLD").role("OLD").build();
        given(technicalSkillReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.of(existing));
        given(cultureReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());

        // when
        resultService.applyCompletion(REQUEST_EVENT_ID, success(2, "unavailable", "encrypted"));

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.COMPLETED);
        assertThat(submission.getPortfolioStatus()).isEqualTo("unavailable");
        assertThat(submission.getPortfolioIssue()).isEqualTo("encrypted");
        assertThat(existing.getJob()).isEqualTo("DEVELOPER");
    }

    @Test
    @DisplayName("실패 결과는 FAILED·ANALYSIS_FAILED와 error의 stage·code를 저장하고 리포트는 건드리지 않는다")
    void applyCompletion_Failure_MarksFailedWithoutTouchingReports() {
        // given
        Applicant applicant = applicant(2);
        ApplicantAnalysisSubmission submission = requested(applicant, 2);
        givenLockedApplicantWith(applicant, submission);

        // when
        resultService.applyCompletion(REQUEST_EVENT_ID, failure(2, "PORTFOLIO_DOWNLOAD", "S3_DOWNLOAD_FAILED"));

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.FAILED);
        assertThat(submission.getFailureReason()).isEqualTo(AnalysisFailureReason.ANALYSIS_FAILED);
        assertThat(submission.getErrorStage()).isEqualTo("PORTFOLIO_DOWNLOAD");
        assertThat(submission.getErrorCode()).isEqualTo("S3_DOWNLOAD_FAILED");
        assertThat(submission.getFailedAt()).isNotNull();
        assertThat(submission.getCompletedAt()).isNull();
        verifyNoInteractions(technicalSkillReportRepository, cultureReportRepository);
    }

    @Test
    @DisplayName("outcome이 없는 결과(AI 배포 전)는 성공으로 처리한다")
    void applyCompletion_NullOutcome_TreatedAsSuccess() {
        // given
        Applicant applicant = applicant(2);
        ApplicantAnalysisSubmission submission = requested(applicant, 2);
        givenLockedApplicantWith(applicant, submission);
        given(technicalSkillReportRepository.findByApplicant_ApplicantId(1L)).willReturn(Optional.empty());

        // when
        resultService.applyCompletion(REQUEST_EVENT_ID, new ApplicantAnalysisCompletedData(
                1L, 2, null, List.of("Java"), "DEVELOPER", "BACKEND", null, null, null, null, null));

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.COMPLETED);
        assertThat(submission.getPortfolioStatus()).isNull();
    }

    @Test
    @DisplayName("과거 버전의 결과는 아무것도 바꾸지 않는다")
    void applyCompletion_StaleVersion_Ignored() {
        // given
        Applicant applicant = applicant(2);
        given(applicantRepository.findWithLockByApplicantId(1L)).willReturn(Optional.of(applicant));

        // when
        resultService.applyCompletion("request-event-1", success(1, "pdf", null));

        // then
        verifyNoInteractions(submissionRepository, technicalSkillReportRepository, cultureReportRepository);
    }

    @Test
    @DisplayName("제출한 적이 없는 지원자의 결과는 아무것도 바꾸지 않는다")
    void applyCompletion_NeverSubmitted_Ignored() {
        // given
        given(applicantRepository.findWithLockByApplicantId(1L)).willReturn(Optional.of(applicant(null)));

        // when
        resultService.applyCompletion(REQUEST_EVENT_ID, success(1, "pdf", null));

        // then
        verifyNoInteractions(submissionRepository, technicalSkillReportRepository, cultureReportRepository);
    }

    @Test
    @DisplayName("버전은 현재 버전과 같은데 그 버전의 제출 건 행이 없으면 아무것도 바꾸지 않는다")
    void applyCompletion_SubmissionRowMissing_Ignored() {
        // given
        Applicant applicant = applicant(2);
        given(applicantRepository.findWithLockByApplicantId(1L)).willReturn(Optional.of(applicant));
        given(submissionRepository.findByApplicantAndSubmissionVersion(applicant, 2)).willReturn(Optional.empty());

        // when
        resultService.applyCompletion(REQUEST_EVENT_ID, success(2, "pdf", null));

        // then
        assertThat(applicant.getCurrentSubmissionVersion()).isEqualTo(2);
        verifyNoInteractions(technicalSkillReportRepository, cultureReportRepository);
    }

    @Test
    @DisplayName("실패 결과에 error가 없으면 지금처럼 stage·code를 null로 두고 FAILED로 바꾼다")
    void applyCompletion_FailureWithoutError_MarksFailedWithNullError() {
        // given
        Applicant applicant = applicant(2);
        ApplicantAnalysisSubmission submission = requested(applicant, 2);
        givenLockedApplicantWith(applicant, submission);

        // when
        resultService.applyCompletion(REQUEST_EVENT_ID, new ApplicantAnalysisCompletedData(
                1L, 2, ApplicantAnalysisCompletedData.OUTCOME_FAILED, null, null, null, null, null, null, null, null));

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.FAILED);
        assertThat(submission.getFailureReason()).isEqualTo(AnalysisFailureReason.ANALYSIS_FAILED);
        assertThat(submission.getErrorStage()).isNull();
        assertThat(submission.getErrorCode()).isNull();
        verifyNoInteractions(technicalSkillReportRepository, cultureReportRepository);
    }

    @Test
    @DisplayName("correlation_id가 저장한 요청 event_id와 다르면 아무것도 바꾸지 않는다")
    void applyCompletion_DifferentCorrelationId_Ignored() {
        // given
        Applicant applicant = applicant(2);
        ApplicantAnalysisSubmission submission = requested(applicant, 2);
        givenLockedApplicantWith(applicant, submission);

        // when
        resultService.applyCompletion("other-request", success(2, "pdf", null));
        resultService.applyCompletion(null, success(2, "pdf", null));

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.REQUESTED);
        assertThat(submission.getPortfolioStatus()).isNull();
        verifyNoInteractions(technicalSkillReportRepository, cultureReportRepository);
    }

    @Test
    @DisplayName("이미 COMPLETED인 제출 건에 중복 완료가 오면 상태·리포트를 바꾸지 않는다")
    void applyCompletion_DuplicateCompletion_Ignored() {
        // given
        Applicant applicant = applicant(2);
        ApplicantAnalysisSubmission submission = requested(applicant, 2);
        submission.markCompleted("pdf", null, OffsetDateTime.now());
        OffsetDateTime completedAt = submission.getCompletedAt();
        givenLockedApplicantWith(applicant, submission);

        // when
        resultService.applyCompletion(REQUEST_EVENT_ID, success(2, "unavailable", "encrypted"));
        resultService.applyCompletion(REQUEST_EVENT_ID, failure(2, "ANALYSIS", "INTERNAL"));

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.COMPLETED);
        assertThat(submission.getPortfolioStatus()).isEqualTo("pdf");
        assertThat(submission.getCompletedAt()).isEqualTo(completedAt);
        assertThat(submission.getErrorStage()).isNull();
        verifyNoInteractions(technicalSkillReportRepository, cultureReportRepository);
    }

    @Test
    @DisplayName("FAILED 뒤에 늦게 온 성공 결과는 반영하지 않는다(#207 범위)")
    void applyCompletion_SuccessAfterFailed_Ignored() {
        // given
        Applicant applicant = applicant(2);
        ApplicantAnalysisSubmission submission = requested(applicant, 2);
        submission.markAnalysisFailed("ANALYSIS", "LLM_UNAVAILABLE", OffsetDateTime.now());
        givenLockedApplicantWith(applicant, submission);

        // when
        resultService.applyCompletion(REQUEST_EVENT_ID, success(2, "pdf", null));

        // then
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.FAILED);
        assertThat(submission.getCompletedAt()).isNull();
        verifyNoInteractions(technicalSkillReportRepository, cultureReportRepository);
    }

    @Test
    @DisplayName("지원자가 없으면 APPLICANT_NOT_FOUND 예외가 발생한다")
    void applyCompletion_ApplicantNotFound_Throws() {
        // given
        given(applicantRepository.findWithLockByApplicantId(1L)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> resultService.applyCompletion(REQUEST_EVENT_ID, success(2, "pdf", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);
    }

    private void givenLockedApplicantWith(Applicant applicant, ApplicantAnalysisSubmission submission) {
        given(applicantRepository.findWithLockByApplicantId(1L)).willReturn(Optional.of(applicant));
        given(submissionRepository.findByApplicantAndSubmissionVersion(applicant, submission.getSubmissionVersion()))
                .willReturn(Optional.of(submission));
    }

    private Applicant applicant(Integer currentVersion) {
        return Applicant.builder().applicantId(1L).currentSubmissionVersion(currentVersion).build();
    }

    private ApplicantAnalysisSubmission requested(Applicant applicant, int version) {
        return ApplicantAnalysisSubmission.request(applicant, version, REQUEST_EVENT_ID, Map.of(),
                "a".repeat(64), OffsetDateTime.now(), Duration.ofMinutes(30));
    }

    private ApplicantAnalysisCompletedData success(int version, String portfolioStatus, String portfolioIssue) {
        return new ApplicantAnalysisCompletedData(
                1L, version, ApplicantAnalysisCompletedData.OUTCOME_SUCCEEDED, List.of("Java"), "DEVELOPER", "BACKEND",
                CulturefitStyle.INCLUSIVE_INNOVATOR.name(), List.of("협업"), portfolioStatus, portfolioIssue, null);
    }

    private ApplicantAnalysisCompletedData failure(int version, String stage, String code) {
        return new ApplicantAnalysisCompletedData(
                1L, version, ApplicantAnalysisCompletedData.OUTCOME_FAILED, null, null, null, null, null, null, null,
                new ApplicantAnalysisCompletedData.ErrorData(stage, code));
    }
}

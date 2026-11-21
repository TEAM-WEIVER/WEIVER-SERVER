package com.weiver.applicant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.weiver.analysis.event.ApplicantAnalysisEventService;
import com.weiver.analysis.event.dto.ApplicantAnalysisRequestedData;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAnalysisSubmission;
import com.weiver.applicant.repository.ApplicantAnalysisSubmissionRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.AnalysisSubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApplicantAnalysisSubmissionServiceTest {

    private static final Duration DEADLINE = Duration.ofMinutes(30);

    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock private ApplicantAnalysisSubmissionRepository submissionRepository;
    @Mock private ApplicantRepository applicantRepository;
    @Mock private ApplicantAnalysisSnapshotService snapshotService;
    @Mock private ApplicantAnalysisEventService applicantAnalysisEventService;

    private ApplicantAnalysisSubmissionService submissionService;

    @BeforeEach
    void setUp() {
        submissionService = new ApplicantAnalysisSubmissionService(
                submissionRepository, applicantRepository, snapshotService, applicantAnalysisEventService, objectMapper, DEADLINE);
    }

    @Test
    @DisplayName("첫 제출은 버전 1 제출 건을 REQUESTED로 저장하고, 같은 event_id로 분석 요청 1개를 발행한다")
    void submit_FirstSubmission_SavesRequestedAndPublishesOnce() {
        // given
        Applicant applicant = Applicant.builder().applicantId(1L).name("홍길동").build();
        ApplicantAnalysisRequestedData data = data(1);
        given(snapshotService.assemble(applicant, 1)).willReturn(data);
        given(snapshotService.contentHash(anyMap())).willReturn("a".repeat(64));
        given(submissionRepository.save(any(ApplicantAnalysisSubmission.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(applicantRepository.updateCurrentSubmissionVersion(1L, 1)).willReturn(1);
        OffsetDateTime before = OffsetDateTime.now();

        // when
        submissionService.submit(applicant);

        // then
        ArgumentCaptor<ApplicantAnalysisSubmission> saved = ArgumentCaptor.forClass(ApplicantAnalysisSubmission.class);
        verify(submissionRepository).save(saved.capture());
        ApplicantAnalysisSubmission submission = saved.getValue();
        assertThat(submission.getApplicant()).isSameAs(applicant);
        assertThat(submission.getSubmissionVersion()).isEqualTo(1);
        assertThat(submission.getStatus()).isEqualTo(AnalysisSubmissionStatus.REQUESTED);
        assertThat(submission.getContentHash()).isEqualTo("a".repeat(64));
        assertThat(submission.getEventId()).hasSize(36);
        assertThat(submission.getRequestedAt()).isBetween(before, OffsetDateTime.now());
        assertThat(submission.getDeadlineAt()).isEqualTo(submission.getRequestedAt().plus(DEADLINE));
        assertThat(submission.getFailureReason()).isNull();
        assertThat(submission.getCompletedAt()).isNull();
        assertThat(submission.getFailedAt()).isNull();
        // 스냅샷은 요청 data 전체이며 submission_version을 포함한다.
        assertThat(submission.getSnapshot()).containsEntry("submission_version", 1);
        assertThat(submission.getSnapshot()).containsEntry("applicant_id", 1L);

        // 포인터는 UPDATE 쿼리로 바꾸고 관리 엔티티 필드도 맞춘다.
        verify(applicantRepository).updateCurrentSubmissionVersion(1L, 1);
        assertThat(applicant.getCurrentSubmissionVersion()).isEqualTo(1);
        verify(applicantAnalysisEventService).publishApplicantAnalysisRequested(submission.getEventId(), data);
    }

    @Test
    @DisplayName("이전 제출이 있으면 다음 버전(current+1)으로 제출한다")
    void submit_Resubmission_IncrementsVersion() {
        // given
        Applicant applicant = Applicant.builder().applicantId(1L).currentSubmissionVersion(2).build();
        given(snapshotService.assemble(applicant, 3)).willReturn(data(3));
        given(snapshotService.contentHash(anyMap())).willReturn("b".repeat(64));
        given(submissionRepository.save(any(ApplicantAnalysisSubmission.class)))
                .willAnswer(invocation -> invocation.getArgument(0));
        given(applicantRepository.updateCurrentSubmissionVersion(1L, 3)).willReturn(1);

        // when
        ApplicantAnalysisSubmission submission = submissionService.submit(applicant);

        // then
        assertThat(submission.getSubmissionVersion()).isEqualTo(3);
        verify(applicantRepository).updateCurrentSubmissionVersion(1L, 3);
        assertThat(applicant.getCurrentSubmissionVersion()).isEqualTo(3);
    }

    @Test
    @DisplayName("제출한 적이 없으면 현재 제출 건 조회는 DB를 읽지 않고 빈 값이다")
    void findCurrent_NeverSubmitted_ReturnsEmptyWithoutQuery() {
        // given
        Applicant applicant = Applicant.builder().applicantId(1L).build();

        // when & then
        assertThat(submissionService.findCurrent(applicant)).isEmpty();
        assertThat(submissionService.isCurrentRequested(applicant)).isFalse();
        verifyNoInteractions(submissionRepository);
    }

    @Test
    @DisplayName("현재 제출 건은 지원자와 현재 버전으로 찾고, REQUESTED일 때만 분석 중으로 본다")
    void isCurrentRequested_UsesCurrentVersionRow() {
        // given
        Applicant applicant = Applicant.builder().applicantId(1L).currentSubmissionVersion(2).build();
        ApplicantAnalysisSubmission requested = submission(applicant, 2);
        given(submissionRepository.findByApplicantAndSubmissionVersion(applicant, 2))
                .willReturn(Optional.of(requested));

        // when & then
        assertThat(submissionService.findCurrent(applicant)).contains(requested);
        assertThat(submissionService.isCurrentRequested(applicant)).isTrue();
    }

    @Test
    @DisplayName("현재 제출 건이 COMPLETED일 때만 분석 완료로 보고, 제출한 적이 없으면 미완료다")
    void isCurrentCompleted_UsesCurrentVersionStatus() {
        // given
        Applicant applicant = Applicant.builder().applicantId(1L).currentSubmissionVersion(2).build();
        ApplicantAnalysisSubmission submission = submission(applicant, 2);
        given(submissionRepository.findByApplicantAndSubmissionVersion(applicant, 2))
                .willReturn(Optional.of(submission));

        // when & then
        assertThat(submissionService.isCurrentCompleted(applicant)).isFalse();
        submission.markCompleted("pdf", null, OffsetDateTime.now());
        assertThat(submissionService.isCurrentCompleted(applicant)).isTrue();
        assertThat(submissionService.isCurrentCompleted(Applicant.builder().applicantId(3L).build())).isFalse();
    }

    private ApplicantAnalysisSubmission submission(Applicant applicant, int version) {
        return ApplicantAnalysisSubmission.request(applicant, version, "event-1", java.util.Map.of(),
                "c".repeat(64), OffsetDateTime.now(), DEADLINE);
    }

    private ApplicantAnalysisRequestedData data(int version) {
        return new ApplicantAnalysisRequestedData(
                1L, version, "홍길동", List.of(), List.of(), List.of(), List.of(), List.of(), null);
    }
}

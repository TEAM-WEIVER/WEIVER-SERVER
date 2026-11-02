package com.weiver.global.metrics.service;

import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JdAnalysisStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class AiPendingMetricsServiceTest {

    @Mock private JobPostingRepository jobPostingRepository;
    @Mock private ApplicantRepository applicantRepository;
    @Mock private InterviewSessionRepository interviewSessionRepository;

    @InjectMocks
    private AiPendingMetricsService aiPendingMetricsService;

    @Test
    @DisplayName("flow마다 해당 대기 상태의 건수를 집계한다")
    void aggregate_CountsEachFlow() {
        // given
        given(jobPostingRepository.countByJdAnalysisStatus(JdAnalysisStatus.REQUESTED)).willReturn(1L);
        given(applicantRepository.countByProfileSyncStatus(ProfileSyncStatus.REQUESTED)).willReturn(2L);
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .willReturn(3L);
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED))
                .willReturn(4L);
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.REPORT_REQUESTED))
                .willReturn(5L);

        // when
        Map<AiFlow, AiPendingStat> result = aiPendingMetricsService.aggregate();

        // then
        assertThat(result).containsOnlyKeys(AiFlow.values());
        assertThat(result.get(AiFlow.JD_ANALYSIS).count()).isEqualTo(1);
        assertThat(result.get(AiFlow.PROFILE_SYNC).count()).isEqualTo(2);
        assertThat(result.get(AiFlow.INTERVIEW_QUESTION).count()).isEqualTo(3);
        assertThat(result.get(AiFlow.INTERVIEW_TRANSCRIPT_SAVE).count()).isEqualTo(4);
        assertThat(result.get(AiFlow.INTERVIEW_REPORT).count()).isEqualTo(5);
    }

    @Test
    @DisplayName("flow마다 가장 이른 요청 시각부터 지금까지의 시간을 최장 대기 시간(초)으로 계산한다")
    void aggregate_ComputesOldestSecondsFromOldestRequestedAt() {
        // given
        given(jobPostingRepository.countByJdAnalysisStatus(JdAnalysisStatus.REQUESTED)).willReturn(1L);
        given(jobPostingRepository.findOldestJdAnalysisRequestedAt(JdAnalysisStatus.REQUESTED))
                .willReturn(Optional.of(OffsetDateTime.now().minusSeconds(600)));
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .willReturn(0L);
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED))
                .willReturn(0L);
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.REPORT_REQUESTED))
                .willReturn(1L);
        given(interviewSessionRepository.findOldestReportRequestedAt(InterviewSessionStatus.REPORT_REQUESTED))
                .willReturn(Optional.of(LocalDateTime.now().minusSeconds(120)));

        // when
        Map<AiFlow, AiPendingStat> result = aiPendingMetricsService.aggregate();

        // then
        assertThat(result.get(AiFlow.JD_ANALYSIS).oldestSeconds()).isCloseTo(600.0, within(5.0));
        assertThat(result.get(AiFlow.INTERVIEW_REPORT).oldestSeconds()).isCloseTo(120.0, within(5.0));
    }

    @Test
    @DisplayName("요청 시각이 null인 대기 행은 건수에는 들어가고 최장 대기 시간 계산에서는 빠진다")
    void aggregate_CountsNullRequestedAtRows_ButExcludesThemFromOldest() {
        // given
        // 프로필 동기화 대기 3건 중 요청 시각이 있는 행은 1건(5분 전)뿐이다. 리포지토리 MIN은 null 행을 건너뛴다.
        given(applicantRepository.countByProfileSyncStatus(ProfileSyncStatus.REQUESTED)).willReturn(3L);
        given(applicantRepository.findOldestProfileSyncRequestedAt(ProfileSyncStatus.REQUESTED))
                .willReturn(Optional.of(OffsetDateTime.now().minusSeconds(300)));
        // 질문 생성 대기 2건은 모두 요청 시각이 없다(배포 전부터 대기 중인 행).
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .willReturn(2L);
        given(interviewSessionRepository.findOldestQuestionRequestedAt(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .willReturn(Optional.empty());

        // when
        Map<AiFlow, AiPendingStat> result = aiPendingMetricsService.aggregate();

        // then
        assertThat(result.get(AiFlow.PROFILE_SYNC).count()).isEqualTo(3);
        assertThat(result.get(AiFlow.PROFILE_SYNC).oldestSeconds()).isCloseTo(300.0, within(5.0));
        assertThat(result.get(AiFlow.INTERVIEW_QUESTION).count()).isEqualTo(2);
        assertThat(result.get(AiFlow.INTERVIEW_QUESTION).oldestSeconds()).isZero();
    }

    @Test
    @DisplayName("대기 건이 없으면 모든 flow의 건수와 최장 대기 시간이 0이다")
    void aggregate_ReturnsZero_WhenNothingPending() {
        // given
        // 스텁 없음: 모든 count는 0, 모든 MIN은 빈 값

        // when
        Map<AiFlow, AiPendingStat> result = aiPendingMetricsService.aggregate();

        // then
        assertThat(result).containsOnlyKeys(AiFlow.values());
        assertThat(result.values()).allSatisfy(stat -> {
            assertThat(stat.count()).isZero();
            assertThat(stat.oldestSeconds()).isZero();
        });
    }
}

package com.weiver.interview.service;

import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.type.InterviewSessionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class InterviewPendingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock private InterviewSessionRepository interviewSessionRepository;

    @InjectMocks
    private InterviewPendingService interviewPendingService;

    @Test
    @DisplayName("면접 대기 상태 3종을 flow별로 세고, 상태마다 해당 요청 시각 컬럼으로 최장 대기 시간을 계산한다")
    void collect_CountsEachInterviewFlow() {
        // given
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .willReturn(3L);
        given(interviewSessionRepository.findOldestQuestionRequestedAt(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .willReturn(Optional.of(local(NOW.minusSeconds(20))));
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED))
                .willReturn(4L);
        given(interviewSessionRepository.findOldestTranscriptSaveRequestedAt(
                InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED))
                .willReturn(Optional.of(local(NOW.minusSeconds(40))));
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.REPORT_REQUESTED))
                .willReturn(5L);
        given(interviewSessionRepository.findOldestReportRequestedAt(InterviewSessionStatus.REPORT_REQUESTED))
                .willReturn(Optional.of(local(NOW.minusSeconds(120))));

        // when
        Map<AiFlow, AiPendingStat> result = interviewPendingService.collect(NOW);

        // then
        assertThat(result).containsOnlyKeys(
                AiFlow.INTERVIEW_QUESTION, AiFlow.INTERVIEW_TRANSCRIPT_SAVE, AiFlow.INTERVIEW_REPORT);
        assertThat(result.get(AiFlow.INTERVIEW_QUESTION)).isEqualTo(new AiPendingStat(3, 20.0));
        assertThat(result.get(AiFlow.INTERVIEW_TRANSCRIPT_SAVE)).isEqualTo(new AiPendingStat(4, 40.0));
        assertThat(result.get(AiFlow.INTERVIEW_REPORT)).isEqualTo(new AiPendingStat(5, 120.0));
    }

    @Test
    @DisplayName("요청 시각이 null인 대기 세션만 있으면 건수에는 들어가고 최장 대기 시간은 0이다")
    void collect_CountsNullRequestedAtRows_ButExcludesThemFromOldest() {
        // given
        // 질문 생성 대기 2건 모두 요청 시각이 없다(컬럼 추가 전부터 대기 중인 행). 나머지 상태는 스텁 없음(0건).
        given(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .willReturn(2L);
        given(interviewSessionRepository.findOldestQuestionRequestedAt(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .willReturn(Optional.empty());

        // when
        Map<AiFlow, AiPendingStat> result = interviewPendingService.collect(NOW);

        // then
        assertThat(result.get(AiFlow.INTERVIEW_QUESTION)).isEqualTo(new AiPendingStat(2, 0.0));
    }

    @Test
    @DisplayName("대기 세션이 없으면 면접 flow 3종의 건수와 최장 대기 시간이 0이다")
    void collect_ReturnsZero_WhenNothingPending() {
        // given
        // 스텁 없음: count는 0, MIN은 빈 값

        // when
        Map<AiFlow, AiPendingStat> result = interviewPendingService.collect(NOW);

        // then
        assertThat(result.values()).hasSize(3).allSatisfy(stat -> assertThat(stat).isEqualTo(new AiPendingStat(0, 0.0)));
    }

    /** InterviewSession 요청 시각은 JVM 기본 시간대의 LocalDateTime으로 저장된다. */
    private LocalDateTime local(Instant instant) {
        return LocalDateTime.ofInstant(instant, ZoneId.systemDefault());
    }
}

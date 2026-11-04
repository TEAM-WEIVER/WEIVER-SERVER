package com.weiver.global.metrics.service;

import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.service.ProfileSyncPendingService;
import com.weiver.global.metrics.AiPendingSource;
import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.interview.repository.InterviewSessionRepository;
import com.weiver.interview.service.InterviewPendingService;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.service.JdAnalysisPendingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class AiPendingMetricsServiceTest {

    @Test
    @DisplayName("컨텍스트별 대기 집계를 같은 기준 시각으로 모아 flow별 결과 하나로 합친다")
    void aggregate_MergesSourcesWithSameNow() {
        // given
        List<Instant> receivedNows = new ArrayList<>();
        AiPendingSource jd = now -> {
            receivedNows.add(now);
            return Map.of(AiFlow.JD_ANALYSIS, new AiPendingStat(1, 10));
        };
        AiPendingSource interview = now -> {
            receivedNows.add(now);
            return Map.of(
                    AiFlow.INTERVIEW_QUESTION, new AiPendingStat(2, 20),
                    AiFlow.INTERVIEW_REPORT, new AiPendingStat(3, 30));
        };
        AiPendingMetricsService service = new AiPendingMetricsService(List.of(jd, interview));

        // when
        Map<AiFlow, AiPendingStat> result = service.aggregate();

        // then
        assertThat(result).containsOnlyKeys(AiFlow.JD_ANALYSIS, AiFlow.INTERVIEW_QUESTION, AiFlow.INTERVIEW_REPORT);
        assertThat(result.get(AiFlow.INTERVIEW_REPORT)).isEqualTo(new AiPendingStat(3, 30));
        assertThat(receivedNows).hasSize(2);
        assertThat(receivedNows.get(0)).isEqualTo(receivedNows.get(1));
    }

    @Test
    @DisplayName("컨텍스트 집계 3종(JD·프로필·면접)을 합치면 flow 5종이 빠짐없이 나온다")
    void aggregate_CoversAllFlows_WithContextSources() {
        // given
        AiPendingMetricsService service = new AiPendingMetricsService(List.of(
                new JdAnalysisPendingService(mock(JobPostingRepository.class)),
                new ProfileSyncPendingService(mock(ApplicantRepository.class)),
                new InterviewPendingService(mock(InterviewSessionRepository.class))
        ));

        // when
        Map<AiFlow, AiPendingStat> result = service.aggregate();

        // then
        assertThat(result).containsOnlyKeys(AiFlow.values());
    }

    @Test
    @DisplayName("두 집계가 같은 flow를 내면 어느 값이 맞는지 알 수 없으므로 예외가 발생한다")
    void aggregate_ThrowsOnDuplicateFlow() {
        // given
        AiPendingSource first = now -> Map.of(AiFlow.PROFILE_SYNC, new AiPendingStat(1, 0));
        AiPendingSource second = now -> Map.of(AiFlow.PROFILE_SYNC, new AiPendingStat(2, 0));
        AiPendingMetricsService service = new AiPendingMetricsService(List.of(first, second));

        // when & then
        assertThatThrownBy(service::aggregate)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("profile_sync");
    }
}

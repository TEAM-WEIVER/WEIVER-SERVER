package com.weiver.jobposting.service;

import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.jobposting.repository.JobPostingRepository;
import com.weiver.jobposting.type.JdAnalysisStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class JdAnalysisPendingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock private JobPostingRepository jobPostingRepository;

    @InjectMocks
    private JdAnalysisPendingService jdAnalysisPendingService;

    @Test
    @DisplayName("REQUESTED 공고 수와 가장 이른 요청 시각으로 jd_analysis 대기 집계를 만든다")
    void collect_CountsRequestedAndComputesOldest() {
        // given
        given(jobPostingRepository.countByJdAnalysisStatus(JdAnalysisStatus.REQUESTED)).willReturn(3L);
        given(jobPostingRepository.findOldestJdAnalysisRequestedAt(JdAnalysisStatus.REQUESTED))
                .willReturn(Optional.of(OffsetDateTime.ofInstant(NOW.minusSeconds(600), ZoneOffset.UTC)));

        // when
        Map<AiFlow, AiPendingStat> result = jdAnalysisPendingService.collect(NOW);

        // then
        assertThat(result).containsOnlyKeys(AiFlow.JD_ANALYSIS);
        assertThat(result.get(AiFlow.JD_ANALYSIS)).isEqualTo(new AiPendingStat(3, 600.0));
    }

    @Test
    @DisplayName("요청 시각이 null인 대기 공고만 있으면 건수에는 들어가고 최장 대기 시간은 0이다")
    void collect_CountsNullRequestedAtRows_ButExcludesThemFromOldest() {
        // given
        // 리포지토리 MIN은 요청 시각이 null인 행을 건너뛰므로, null 행만 있으면 빈 값이다.
        given(jobPostingRepository.countByJdAnalysisStatus(JdAnalysisStatus.REQUESTED)).willReturn(2L);
        given(jobPostingRepository.findOldestJdAnalysisRequestedAt(JdAnalysisStatus.REQUESTED))
                .willReturn(Optional.empty());

        // when
        Map<AiFlow, AiPendingStat> result = jdAnalysisPendingService.collect(NOW);

        // then
        assertThat(result.get(AiFlow.JD_ANALYSIS)).isEqualTo(new AiPendingStat(2, 0.0));
    }

    @Test
    @DisplayName("대기 공고가 없으면 건수와 최장 대기 시간이 0이다")
    void collect_ReturnsZero_WhenNothingPending() {
        // given
        // 스텁 없음: count는 0, MIN은 빈 값

        // when
        Map<AiFlow, AiPendingStat> result = jdAnalysisPendingService.collect(NOW);

        // then
        assertThat(result.get(AiFlow.JD_ANALYSIS)).isEqualTo(new AiPendingStat(0, 0.0));
    }
}

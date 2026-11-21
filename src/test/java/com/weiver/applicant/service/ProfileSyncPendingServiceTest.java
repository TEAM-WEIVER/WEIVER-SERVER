package com.weiver.applicant.service;

import com.weiver.applicant.repository.ApplicantAnalysisSubmissionRepository;
import com.weiver.applicant.type.AnalysisSubmissionStatus;
import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
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
class ProfileSyncPendingServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Mock private ApplicantAnalysisSubmissionRepository submissionRepository;

    @InjectMocks
    private ProfileSyncPendingService profileSyncPendingService;

    @Test
    @DisplayName("REQUESTED 제출 건 수와 가장 이른 요청 시각으로 profile_sync 대기 집계를 만든다")
    void collect_CountsRequestedAndComputesOldest() {
        // given
        // 대기 3건 중 가장 이른 요청 시각은 5분 전이다.
        given(submissionRepository.countByStatus(AnalysisSubmissionStatus.REQUESTED)).willReturn(3L);
        given(submissionRepository.findOldestRequestedAt(AnalysisSubmissionStatus.REQUESTED))
                .willReturn(Optional.of(OffsetDateTime.ofInstant(NOW.minusSeconds(300), ZoneOffset.UTC)));

        // when
        Map<AiFlow, AiPendingStat> result = profileSyncPendingService.collect(NOW);

        // then
        assertThat(result).containsOnlyKeys(AiFlow.PROFILE_SYNC);
        assertThat(result.get(AiFlow.PROFILE_SYNC)).isEqualTo(new AiPendingStat(3, 300.0));
    }

    @Test
    @DisplayName("대기 제출 건이 없으면 건수와 최장 대기 시간이 0이다")
    void collect_ReturnsZero_WhenNothingPending() {
        // given
        // 스텁 없음: count는 0, MIN은 빈 값

        // when
        Map<AiFlow, AiPendingStat> result = profileSyncPendingService.collect(NOW);

        // then
        assertThat(result.get(AiFlow.PROFILE_SYNC)).isEqualTo(new AiPendingStat(0, 0.0));
    }
}

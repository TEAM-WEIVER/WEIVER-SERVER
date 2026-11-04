package com.weiver.global.metrics;

import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.service.AiPendingMetricsService;
import com.weiver.global.metrics.type.AiFlow;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class AiPendingMetricsSchedulerTest {

    @Mock private AiPendingMetricsService aiPendingMetricsService;
    @Mock private AiPendingMetrics aiPendingMetrics;

    @InjectMocks
    private AiPendingMetricsScheduler aiPendingMetricsScheduler;

    @Test
    @DisplayName("주기 실행 시 대기 건을 한 번 집계해 게이지 캐시에 반영한다")
    void refresh_AggregatesOnceAndUpdatesGauges() {
        // given
        Map<AiFlow, AiPendingStat> snapshot = Map.of(AiFlow.JD_ANALYSIS, new AiPendingStat(1, 10));
        given(aiPendingMetricsService.aggregate()).willReturn(snapshot);

        // when
        aiPendingMetricsScheduler.refresh();

        // then
        then(aiPendingMetricsService).should().aggregate();
        then(aiPendingMetrics).should().update(snapshot);
    }
}

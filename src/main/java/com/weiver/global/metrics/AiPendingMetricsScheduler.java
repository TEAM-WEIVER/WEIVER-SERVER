package com.weiver.global.metrics;

import com.weiver.global.metrics.service.AiPendingMetricsService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * AI 결과 대기 건을 주기적으로 한 번 집계해 게이지 캐시에 넣는다.
 * 주기는 {@code weiver.metrics.ai-pending.refresh-interval}(기본 30s)로 정한다.
 */
@Component
@RequiredArgsConstructor
public class AiPendingMetricsScheduler {

    private final AiPendingMetricsService aiPendingMetricsService;
    private final AiPendingMetrics aiPendingMetrics;

    @Scheduled(fixedDelayString = "${weiver.metrics.ai-pending.refresh-interval:30s}")
    public void refresh() {
        aiPendingMetrics.update(aiPendingMetricsService.aggregate());
    }
}

package com.weiver.global.metrics.service;

import com.weiver.global.metrics.AiPendingSource;
import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * 컨텍스트별 {@link AiPendingSource} 집계를 같은 기준 시각으로 모아 flow별 결과 하나로 합친다.
 * 대기 지표 게이지가 이 결과를 캐시해 내보낸다.
 */
@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class AiPendingMetricsService {

    private final List<AiPendingSource> aiPendingSources;

    public Map<AiFlow, AiPendingStat> aggregate() {
        Instant now = Instant.now();
        Map<AiFlow, AiPendingStat> result = new EnumMap<>(AiFlow.class);

        for (AiPendingSource source : aiPendingSources) {
            source.collect(now).forEach((flow, stat) -> {
                if (result.putIfAbsent(flow, stat) != null) {
                    throw new IllegalStateException("duplicate ai pending flow: " + flow.getTag());
                }
            });
        }
        return Collections.unmodifiableMap(result);
    }
}

package com.weiver.global.metrics;

import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.function.ToDoubleFunction;

/**
 * AI 결과 대기 게이지. 값은 {@link AiPendingMetricsScheduler}가 주기적으로 넣어 준 집계 결과를 그대로 돌려준다.
 * 지표 수집(scrape) 요청은 이 캐시만 읽고 DB를 조회하지 않는다.
 *
 * <p>첫 집계가 끝나기 전에는 값을 모르므로 NaN을 내보낸다.
 */
@Component
public class AiPendingMetrics {

    static final String PENDING = "weiver.ai.pending";
    static final String PENDING_OLDEST_SECONDS = "weiver.ai.pending.oldest.seconds";
    static final String TAG_FLOW = "flow";

    private volatile Map<AiFlow, AiPendingStat> snapshot = Map.of();

    public AiPendingMetrics(MeterRegistry meterRegistry) {
        for (AiFlow flow : AiFlow.values()) {
            register(meterRegistry, PENDING, "AI 결과를 기다리는 건수", flow, AiPendingStat::count);
            register(meterRegistry, PENDING_OLDEST_SECONDS,
                    "요청 시각이 있는 대기 건 중 가장 오래 기다린 시간(초)", flow, AiPendingStat::oldestSeconds);
        }
    }

    public void update(Map<AiFlow, AiPendingStat> snapshot) {
        this.snapshot = Map.copyOf(snapshot);
    }

    private void register(
            MeterRegistry meterRegistry,
            String name,
            String description,
            AiFlow flow,
            ToDoubleFunction<AiPendingStat> value
    ) {
        Gauge.builder(name, this, metrics -> metrics.read(flow, value))
                .description(description)
                .tag(TAG_FLOW, flow.getTag())
                .register(meterRegistry);
    }

    private double read(AiFlow flow, ToDoubleFunction<AiPendingStat> value) {
        AiPendingStat stat = snapshot.get(flow);
        return stat == null ? Double.NaN : value.applyAsDouble(stat);
    }
}

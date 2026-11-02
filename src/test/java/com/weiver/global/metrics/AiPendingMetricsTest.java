package com.weiver.global.metrics;

import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiPendingMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Test
    @DisplayName("flow 5종마다 대기 건수와 최장 대기 시간 게이지를 flow 태그만 붙여 등록한다")
    void constructor_RegistersGaugesPerFlow() {
        // given
        new AiPendingMetrics(meterRegistry);

        // when
        var pendingGauges = meterRegistry.find("weiver.ai.pending").gauges();
        var oldestGauges = meterRegistry.find("weiver.ai.pending.oldest.seconds").gauges();

        // then
        assertThat(pendingGauges).hasSize(AiFlow.values().length);
        assertThat(oldestGauges).hasSize(AiFlow.values().length);
        assertThat(pendingGauges).extracting(gauge -> gauge.getId().getTag("flow"))
                .containsExactlyInAnyOrder("jd_analysis", "profile_sync", "interview_question",
                        "interview_transcript_save", "interview_report");
        assertThat(pendingGauges).allSatisfy(gauge ->
                assertThat(gauge.getId().getTags()).extracting("key").containsExactly("flow"));
    }

    @Test
    @DisplayName("게이지는 마지막으로 반영한 집계 결과를 돌려준다")
    void update_ChangesGaugeValues() {
        // given
        AiPendingMetrics metrics = new AiPendingMetrics(meterRegistry);
        Map<AiFlow, AiPendingStat> snapshot = new EnumMap<>(AiFlow.class);
        for (AiFlow flow : AiFlow.values()) {
            snapshot.put(flow, new AiPendingStat(0, 0));
        }
        snapshot.put(AiFlow.JD_ANALYSIS, new AiPendingStat(3, 125.5));

        // when
        metrics.update(snapshot);

        // then
        assertThat(gauge("weiver.ai.pending", "jd_analysis").value()).isEqualTo(3.0);
        assertThat(gauge("weiver.ai.pending.oldest.seconds", "jd_analysis").value()).isEqualTo(125.5);
        assertThat(gauge("weiver.ai.pending", "interview_report").value()).isZero();
    }

    @Test
    @DisplayName("첫 집계 전에는 값을 모르므로 게이지가 NaN이다")
    void gauges_AreNaN_BeforeFirstUpdate() {
        // given
        new AiPendingMetrics(meterRegistry);

        // when
        double pending = gauge("weiver.ai.pending", "profile_sync").value();
        double oldest = gauge("weiver.ai.pending.oldest.seconds", "profile_sync").value();

        // then
        assertThat(pending).isNaN();
        assertThat(oldest).isNaN();
    }

    private Gauge gauge(String name, String flow) {
        return meterRegistry.get(name).tag("flow", flow).gauge();
    }
}

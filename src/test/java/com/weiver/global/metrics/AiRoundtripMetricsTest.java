package com.weiver.global.metrics;

import com.weiver.global.metrics.type.AiFlow;
import com.weiver.global.metrics.type.AiOutcome;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class AiRoundtripMetricsTest {

    private final SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    private final AiRoundtripMetrics aiRoundtripMetrics = new AiRoundtripMetrics(meterRegistry);

    @Test
    @DisplayName("요청 시각부터 지금까지의 시간을 flow·outcome 태그로 기록한다")
    void record_RecordsElapsedWithFlowAndOutcomeTags() {
        // given
        OffsetDateTime requestedAt = OffsetDateTime.now().minusSeconds(90);

        // when
        aiRoundtripMetrics.record(AiFlow.JD_ANALYSIS, AiOutcome.COMPLETED, requestedAt);

        // then
        Timer timer = meterRegistry.get("weiver.ai.roundtrip")
                .tag("flow", "jd_analysis")
                .tag("outcome", "completed")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isCloseTo(90.0, within(5.0));
        assertThat(timer.getId().getTags()).extracting("key").containsExactlyInAnyOrder("flow", "outcome");
    }

    @Test
    @DisplayName("LocalDateTime 요청 시각도 같은 방식으로 기록한다")
    void record_RecordsLocalDateTimeRequestedAt() {
        // given
        LocalDateTime requestedAt = LocalDateTime.now().minusSeconds(30);

        // when
        aiRoundtripMetrics.record(AiFlow.INTERVIEW_TRANSCRIPT_SAVE, AiOutcome.FAILED, requestedAt);

        // then
        Timer timer = meterRegistry.get("weiver.ai.roundtrip")
                .tag("flow", "interview_transcript_save")
                .tag("outcome", "failed")
                .timer();
        assertThat(timer.count()).isEqualTo(1);
        assertThat(timer.totalTime(TimeUnit.SECONDS)).isCloseTo(30.0, within(5.0));
    }

    @Test
    @DisplayName("요청 시각이 null이면 기록하지 않는다")
    void record_SkipsWhenRequestedAtIsNull() {
        // given
        OffsetDateTime offsetRequestedAt = null;
        LocalDateTime localRequestedAt = null;

        // when
        aiRoundtripMetrics.record(AiFlow.PROFILE_SYNC, AiOutcome.COMPLETED, offsetRequestedAt);
        aiRoundtripMetrics.record(AiFlow.INTERVIEW_REPORT, AiOutcome.COMPLETED, localRequestedAt);

        // then
        assertThat(meterRegistry.find("weiver.ai.roundtrip").timers()).isEmpty();
    }
}

package com.weiver.global.metrics;

import com.weiver.global.metrics.type.AiFlow;
import com.weiver.global.metrics.type.AiOutcome;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

/**
 * AI 요청→결과 지연 타이머. 결과 이벤트를 처리할 때 요청 시각부터 지금까지의 시간을 기록한다.
 * 요청 시각이 없으면(컬럼 추가 전에 요청된 건) 기록하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class AiRoundtripMetrics {

    static final String ROUNDTRIP = "weiver.ai.roundtrip";
    static final String TAG_FLOW = "flow";
    static final String TAG_OUTCOME = "outcome";

    private final MeterRegistry meterRegistry;

    public void record(AiFlow flow, AiOutcome outcome, OffsetDateTime requestedAt) {
        if (requestedAt == null) {
            return;
        }
        record(flow, outcome, requestedAt.toInstant());
    }

    /** InterviewSession의 요청 시각(LocalDateTime)은 JVM 기본 시간대로 기록하므로 같은 시간대로 바꾼다. */
    public void record(AiFlow flow, AiOutcome outcome, LocalDateTime requestedAt) {
        if (requestedAt == null) {
            return;
        }
        record(flow, outcome, requestedAt.atZone(ZoneId.systemDefault()).toInstant());
    }

    private void record(AiFlow flow, AiOutcome outcome, Instant requestedAt) {
        Timer.builder(ROUNDTRIP)
                .description("AI 요청부터 결과 이벤트 처리까지 걸린 시간")
                .tag(TAG_FLOW, flow.getTag())
                .tag(TAG_OUTCOME, outcome.getTag())
                .register(meterRegistry)
                .record(Duration.between(requestedAt, Instant.now()));
    }
}

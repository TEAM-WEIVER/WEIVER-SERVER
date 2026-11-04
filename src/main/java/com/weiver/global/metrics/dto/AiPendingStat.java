package com.weiver.global.metrics.dto;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * 한 flow의 대기 집계 결과.
 *
 * @param count         대기 건수(요청 시각이 없는 행 포함)
 * @param oldestSeconds 요청 시각이 있는 대기 행 중 가장 오래 기다린 시간(초). 그런 행이 없으면 0
 */
public record AiPendingStat(long count, double oldestSeconds) {

    /**
     * @param oldestRequestedAt 요청 시각이 있는 대기 행 중 가장 이른 요청 시각. 요청 시각이 null인 행은 여기에 넣지 않는다
     */
    public static AiPendingStat of(long count, Optional<Instant> oldestRequestedAt, Instant now) {
        double oldestSeconds = oldestRequestedAt
                .map(requestedAt -> Math.max(0, Duration.between(requestedAt, now).toMillis()) / 1000.0)
                .orElse(0.0);
        return new AiPendingStat(count, oldestSeconds);
    }
}

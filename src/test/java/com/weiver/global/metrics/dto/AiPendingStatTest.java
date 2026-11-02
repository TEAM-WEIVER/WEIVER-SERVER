package com.weiver.global.metrics.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class AiPendingStatTest {

    private static final Instant NOW = Instant.parse("2026-11-02T10:00:00Z");

    @Test
    @DisplayName("가장 이른 요청 시각부터 기준 시각까지를 최장 대기 시간(초)으로 계산한다")
    void of_ComputesOldestSeconds() {
        // given
        Optional<Instant> oldest = Optional.of(NOW.minusMillis(125_500));

        // when
        AiPendingStat stat = AiPendingStat.of(3, oldest, NOW);

        // then
        assertThat(stat.count()).isEqualTo(3);
        assertThat(stat.oldestSeconds()).isEqualTo(125.5);
    }

    @Test
    @DisplayName("요청 시각이 있는 대기 행이 없으면 건수는 그대로 두고 최장 대기 시간은 0이다")
    void of_ReturnsZeroOldest_WhenNoRequestedAt() {
        // given
        Optional<Instant> oldest = Optional.empty();

        // when
        AiPendingStat stat = AiPendingStat.of(2, oldest, NOW);

        // then
        assertThat(stat.count()).isEqualTo(2);
        assertThat(stat.oldestSeconds()).isZero();
    }

    @Test
    @DisplayName("요청 시각이 기준 시각보다 늦으면(시계 차이) 최장 대기 시간을 0으로 둔다")
    void of_ClampsNegativeToZero() {
        // given
        Optional<Instant> oldest = Optional.of(NOW.plusSeconds(5));

        // when
        AiPendingStat stat = AiPendingStat.of(1, oldest, NOW);

        // then
        assertThat(stat.oldestSeconds()).isZero();
    }
}

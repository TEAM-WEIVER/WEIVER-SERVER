package com.weiver.global.metrics;

import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;

import java.time.Instant;
import java.util.Map;

/**
 * AI 결과 대기 건 집계를 내는 쪽. 각 컨텍스트가 자기 엔티티의 대기 상태로 구현한다.
 * global은 이 인터페이스만 알고 컨텍스트에 의존하지 않는다({@code DomainEventHandler}와 같은 방향).
 */
public interface AiPendingSource {

    /**
     * 담당 flow들의 대기 건수와 최장 대기 시간을 계산한다.
     *
     * @param now 최장 대기 시간 계산 기준 시각. 한 번의 집계에서 모든 구현이 같은 값을 받는다.
     */
    Map<AiFlow, AiPendingStat> collect(Instant now);
}

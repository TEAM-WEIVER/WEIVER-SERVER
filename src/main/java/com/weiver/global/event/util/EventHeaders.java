package com.weiver.global.event.util;

/**
 * 이벤트 봉투 밖, AMQP 메시지 헤더로 주고받는 값의 이름.
 * 헤더는 봉투 계약과 별개라 수신 측(AI 서버)이 무시해도 동작에 영향이 없다.
 */
public final class EventHeaders {

    /** 발행 시점의 MDC traceId. 소비 측이 같은 traceId로 로그를 남기는 데 쓴다. */
    public static final String TRACE_ID = "trace_id";

    private EventHeaders() {}
}

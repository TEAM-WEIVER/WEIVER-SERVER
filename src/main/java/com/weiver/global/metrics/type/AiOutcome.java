package com.weiver.global.metrics.type;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * AI 결과 이벤트가 알려 준 처리 결과. 값({@link #tag})은 지표의 {@code outcome} 태그로만 쓴다.
 */
@Getter
@RequiredArgsConstructor
public enum AiOutcome {

    COMPLETED("completed"),
    FAILED("failed");

    private final String tag;
}

package com.weiver.global.speech.service;

import org.springframework.core.NestedExceptionUtils;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.util.concurrent.TimeoutException;

/**
 * 외부 음성 API 호출 실패의 분류 결과. 응답 본문은 담지 않는다(질문·답변 원문 로그 방지).
 *
 * @param httpStatus 공급자가 준 HTTP 상태. 응답을 못 받았으면 null
 * @param timeout    타임아웃 여부
 * @param causeType  가장 안쪽 원인 예외의 종류(예: DataBufferLimitException). 진단용이며 메시지는 남기지 않는다
 */
record SpeechProviderFailure(Integer httpStatus, boolean timeout, String causeType) {

    static SpeechProviderFailure of(Throwable throwable) {
        Throwable cause = Exceptions.unwrap(throwable);
        String causeType = NestedExceptionUtils.getMostSpecificCause(cause).getClass().getSimpleName();
        if (cause instanceof TimeoutException) {
            return new SpeechProviderFailure(null, true, causeType);
        }
        if (cause instanceof WebClientResponseException e) {
            return new SpeechProviderFailure(e.getStatusCode().value(), false, causeType);
        }
        return new SpeechProviderFailure(null, false, causeType);
    }

    boolean isRateLimited() {
        return httpStatus != null && httpStatus == 429;
    }

    String reason() {
        if (timeout) {
            return "timeout";
        }
        if (httpStatus == null) {
            return "connection_error";
        }
        // 2xx인데 예외가 났다면 공급자 오류가 아니라 응답을 읽지 못한 것이다(예: 응답 크기 한도 초과).
        return httpStatus >= 200 && httpStatus < 300 ? "response_read_error" : "http_" + httpStatus;
    }
}

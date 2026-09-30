package com.weiver.global.speech.service;

import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.Exceptions;

import java.util.concurrent.TimeoutException;

/**
 * 외부 음성 API 호출 실패의 분류 결과. 응답 본문은 담지 않는다(질문·답변 원문 로그 방지).
 */
record SpeechProviderFailure(Integer httpStatus, boolean timeout) {

    static SpeechProviderFailure of(Throwable throwable) {
        Throwable cause = Exceptions.unwrap(throwable);
        if (cause instanceof TimeoutException) {
            return new SpeechProviderFailure(null, true);
        }
        if (cause instanceof WebClientResponseException e) {
            return new SpeechProviderFailure(e.getStatusCode().value(), false);
        }
        return new SpeechProviderFailure(null, false);
    }

    boolean isRateLimited() {
        return httpStatus != null && httpStatus == 429;
    }

    String reason() {
        if (timeout) {
            return "timeout";
        }
        return httpStatus != null ? "http_" + httpStatus : "connection_error";
    }
}

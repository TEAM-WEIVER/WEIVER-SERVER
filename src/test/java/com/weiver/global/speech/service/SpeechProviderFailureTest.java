package com.weiver.global.speech.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DataBufferLimitException;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

class SpeechProviderFailureTest {

    private WebClientResponseException responseException(HttpStatus status, Throwable cause) {
        return WebClientResponseException.create(status.value(), status.getReasonPhrase(), null, new byte[0],
                StandardCharsets.UTF_8, null).initCause(cause) == null ? null
                : new WebClientResponseException(status.value(), status.getReasonPhrase(), null, null,
                StandardCharsets.UTF_8) {
                    @Override
                    public synchronized Throwable getCause() {
                        return cause;
                    }
                };
    }

    @Test
    @DisplayName("2xx 응답인데 실패했다면 http_200이 아니라 response_read_error로 분류하고 원인 예외 종류를 남긴다")
    void classifiesReadFailureOn2xx() {
        WebClientResponseException e = responseException(HttpStatus.OK, new DataBufferLimitException("limit"));

        SpeechProviderFailure failure = SpeechProviderFailure.of(e);

        assertThat(failure.reason()).isEqualTo("response_read_error");
        assertThat(failure.causeType()).isEqualTo("DataBufferLimitException");
        assertThat(failure.isRateLimited()).isFalse();
    }

    @Test
    @DisplayName("4xx·5xx는 http_상태로, 429는 한도 초과로 분류한다")
    void classifiesHttpErrors() {
        assertThat(SpeechProviderFailure.of(responseException(HttpStatus.FORBIDDEN, new RuntimeException())).reason())
                .isEqualTo("http_403");
        assertThat(SpeechProviderFailure.of(responseException(HttpStatus.SERVICE_UNAVAILABLE, new RuntimeException())).reason())
                .isEqualTo("http_503");
        assertThat(SpeechProviderFailure.of(responseException(HttpStatus.TOO_MANY_REQUESTS, new RuntimeException())).isRateLimited())
                .isTrue();
    }

    @Test
    @DisplayName("타임아웃과 연결 오류를 구분한다")
    void classifiesTimeoutAndConnectionError() {
        assertThat(SpeechProviderFailure.of(new TimeoutException()).reason()).isEqualTo("timeout");
        assertThat(SpeechProviderFailure.of(new IllegalStateException("x")).reason()).isEqualTo("connection_error");
    }
}

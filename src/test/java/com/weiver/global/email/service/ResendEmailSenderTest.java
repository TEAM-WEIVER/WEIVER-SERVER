package com.weiver.global.email.service;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import com.weiver.global.email.config.ResendProperties;
import com.weiver.global.email.dto.EmailSendRequest;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailSenderTest {

    private MockWebServer mockWebServer;
    private ResendEmailSender resendEmailSender;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();

        String baseUrl = mockWebServer.url("/").toString();

        WebClient webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("Authorization", "Bearer test-key")
                .defaultHeader("Content-Type", "application/json")
                .build();

        ResendProperties properties = new ResendProperties(
                "test-api-key", baseUrl, "noreply@weiver.com", "WEIVER"
        );

        resendEmailSender = new ResendEmailSender(webClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    @Test
    @DisplayName("정상 응답(200) 시 예외 없이 성공한다.")
    void send_success() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        assertThatNoException().isThrownBy(() ->
                resendEmailSender.send(EmailSendRequest.ofHtml("to@test.com", "제목", "<p>내용</p>")));
    }

    @Test
    @DisplayName("4xx 응답 시 EMAIL_SEND_FAILED 예외가 발생한다.")
    void send_4xxError() {
        mockWebServer.enqueue(new MockResponse()
                .setResponseCode(422)
                .setBody("{\"error\": \"invalid_to\"}"));

        assertThatThrownBy(() ->
                resendEmailSender.send(EmailSendRequest.ofHtml("to@test.com", "제목", "<p>내용</p>")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    @Test
    @DisplayName("5xx 응답 시 EMAIL_SEND_FAILED 예외가 발생한다.")
    void send_5xxError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        assertThatThrownBy(() ->
                resendEmailSender.send(EmailSendRequest.ofHtml("to@test.com", "제목", "<p>내용</p>")))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    @Test
    @DisplayName("null 요청 시 EMAIL_SEND_FAILED 예외가 발생한다.")
    void send_nullRequest() {
        assertThatThrownBy(() -> resendEmailSender.send(null))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    @Test
    @DisplayName("수신자(to)가 없으면 EMAIL_SEND_FAILED 예외가 발생한다.")
    void send_missingTo() {
        assertThatThrownBy(() ->
                resendEmailSender.send(new EmailSendRequest(null, "제목", "내용", null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }

    @Test
    @DisplayName("html/text 본문이 모두 없으면 EMAIL_SEND_FAILED 예외가 발생한다.")
    void send_missingContent() {
        assertThatThrownBy(() ->
                resendEmailSender.send(new EmailSendRequest("to@test.com", "제목", null, null)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);
    }
}
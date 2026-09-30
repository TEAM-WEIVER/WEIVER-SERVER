package com.weiver.global.speech.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.speech.SpeechTestProperties;
import com.weiver.global.speech.dto.Transcription;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GroqSpeechTranscriberTest {

    private static final byte[] AUDIO = "fake-webm-audio".getBytes();
    private static final String API_KEY = "test-groq-key";

    private MockWebServer mockWebServer;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        baseUrl = mockWebServer.url("/openai/v1").toString();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private GroqSpeechTranscriber transcriber(String apiKey, String prompt, Duration timeout) {
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
        return new GroqSpeechTranscriber(webClient, SpeechTestProperties.of(baseUrl, apiKey, prompt, timeout));
    }

    private GroqSpeechTranscriber transcriber() {
        return transcriber(API_KEY, "", Duration.ofSeconds(5));
    }

    private MockResponse json(String body) {
        return new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private void assertFailure(Runnable call, ErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expected);
    }

    @Test
    @DisplayName("성공하면 인식 텍스트와 오디오 길이를 돌려주고, 요청은 설정값대로 multipart로 전송된다.")
    void transcribe_success() throws InterruptedException {
        mockWebServer.enqueue(json("{\"text\":\"Kafka와 RabbitMQ를 사용했습니다.\",\"duration\":12.5,\"language\":\"korean\",\"segments\":[]}"));

        Transcription result = transcriber().transcribe(AUDIO, "answer.webm", "audio/webm");

        assertThat(result.text()).isEqualTo("Kafka와 RabbitMQ를 사용했습니다.");
        assertThat(result.durationSeconds()).isEqualTo(12.5);

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/openai/v1/audio/transcriptions");
        assertThat(request.getHeader("Authorization")).isEqualTo("Bearer " + API_KEY);
        assertThat(request.getHeader("Content-Type")).startsWith("multipart/form-data");

        String body = request.getBody().readUtf8();
        assertThat(body).contains("name=\"file\"").contains("filename=\"answer.webm\"").contains("fake-webm-audio");
        assertThat(body).contains("name=\"model\"").contains(SpeechTestProperties.STT_MODEL);
        assertThat(body).contains("name=\"language\"").contains("ko");
        assertThat(body).contains("name=\"temperature\"");
        assertThat(body).contains("name=\"response_format\"").contains("verbose_json");
        assertThat(body).doesNotContain("name=\"prompt\"");
    }

    @Test
    @DisplayName("prompt 힌트가 설정되면 요청에 포함된다.")
    void transcribe_withPrompt() throws InterruptedException {
        mockWebServer.enqueue(json("{\"text\":\"안녕하세요\",\"duration\":1.0}"));

        transcriber(API_KEY, "Kafka, RabbitMQ", Duration.ofSeconds(5)).transcribe(AUDIO, "a.webm", "audio/webm");

        String body = mockWebServer.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8();
        assertThat(body).contains("name=\"prompt\"").contains("Kafka, RabbitMQ");
    }

    @Test
    @DisplayName("응답에 duration이 없으면 길이는 null로 돌려준다.")
    void transcribe_missingDuration() {
        mockWebServer.enqueue(json("{\"text\":\"답변입니다\"}"));

        Transcription result = transcriber().transcribe(AUDIO, "a.webm", "audio/webm");

        assertThat(result.text()).isEqualTo("답변입니다");
        assertThat(result.durationSeconds()).isNull();
    }

    @Test
    @DisplayName("text가 비어 오면 빈 문자열로 돌려준다.")
    void transcribe_emptyText() {
        mockWebServer.enqueue(json("{\"duration\":2.0}"));

        Transcription result = transcriber().transcribe(AUDIO, "a.webm", "audio/webm");

        assertThat(result.text()).isEmpty();
    }

    @Test
    @DisplayName("429 응답이면 SPEECH_PROVIDER_RATE_LIMITED 예외가 발생한다.")
    void transcribe_rateLimited() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429).setBody("{\"error\":\"rate_limit\"}"));

        assertFailure(() -> transcriber().transcribe(AUDIO, "a.webm", "audio/webm"),
                ErrorCode.SPEECH_PROVIDER_RATE_LIMITED);
    }

    @Test
    @DisplayName("4xx(400·401·403) 응답이면 SPEECH_TRANSCRIPTION_FAILED 예외가 발생한다.")
    void transcribe_4xxError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));
        mockWebServer.enqueue(new MockResponse().setResponseCode(401));
        mockWebServer.enqueue(new MockResponse().setResponseCode(403));

        for (int i = 0; i < 3; i++) {
            assertFailure(() -> transcriber().transcribe(AUDIO, "a.webm", "audio/webm"),
                    ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
        }
    }

    @Test
    @DisplayName("5xx 응답이면 SPEECH_TRANSCRIPTION_FAILED 예외가 발생하고 재시도하지 않는다.")
    void transcribe_5xxError() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(503));

        assertFailure(() -> transcriber().transcribe(AUDIO, "a.webm", "audio/webm"),
                ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
        assertThat(mockWebServer.getRequestCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("타임아웃이 나면 SPEECH_TRANSCRIPTION_FAILED 예외가 발생한다.")
    void transcribe_timeout() {
        mockWebServer.enqueue(json("{\"text\":\"늦은 응답\",\"duration\":1.0}")
                .setHeadersDelay(2, TimeUnit.SECONDS));

        assertFailure(() -> transcriber(API_KEY, "", Duration.ofMillis(300)).transcribe(AUDIO, "a.webm", "audio/webm"),
                ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
    }

    @Test
    @DisplayName("응답 본문이 비어 있으면 SPEECH_TRANSCRIPTION_FAILED 예외가 발생한다.")
    void transcribe_emptyBody() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200));

        assertFailure(() -> transcriber().transcribe(AUDIO, "a.webm", "audio/webm"),
                ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
    }

    @Test
    @DisplayName("API 키가 비어 있으면 외부 호출 없이 SPEECH_TRANSCRIPTION_FAILED 예외가 발생한다.")
    void transcribe_blankApiKey() {
        assertFailure(() -> transcriber("", "", Duration.ofSeconds(5)).transcribe(AUDIO, "a.webm", "audio/webm"),
                ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
        assertThat(mockWebServer.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("빈 오디오면 외부 호출 없이 SPEECH_TRANSCRIPTION_FAILED 예외가 발생한다.")
    void transcribe_emptyAudio() {
        assertFailure(() -> transcriber().transcribe(new byte[0], "a.webm", "audio/webm"),
                ErrorCode.SPEECH_TRANSCRIPTION_FAILED);
        assertThat(mockWebServer.getRequestCount()).isZero();
    }
}

package com.weiver.global.speech.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.speech.SpeechTestProperties;
import com.weiver.global.speech.config.SpeechConfig;
import com.weiver.global.speech.config.SpeechProperties;
import com.weiver.global.speech.dto.SynthesizedAudio;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 실제 TTS 응답 크기(수백 KB~수 MB의 base64 WAV)를 운영과 같은 방식으로 만든 WebClient로 받아 보는 테스트.
 * WebClient 기본 메모리 한도(256KB)에 걸리면 Gemini가 200을 줘도 서버가 응답을 읽지 못한다.
 */
class GeminiSpeechSynthesizerLargeResponseTest {

    private static final String QUESTION = "Kafka와 RabbitMQ를 함께 사용한 이유가 무엇인가요?";

    private MockWebServer mockWebServer;
    private GeminiSpeechSynthesizer synthesizer;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        String baseUrl = mockWebServer.url("/v1beta").toString();

        SpeechProperties properties = SpeechTestProperties.of(baseUrl, "test-key", "", Duration.ofSeconds(10));
        // 운영과 같은 빈 설정으로 WebClient를 만든다(테스트가 자체 WebClient를 만들면 이 문제를 놓친다).
        WebClient webClient = new SpeechConfig().geminiWebClient(WebClient.builder(), properties);
        synthesizer = new GeminiSpeechSynthesizer(webClient, properties);
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private byte[] wavOfBytes(int size) {
        byte[] wav = new byte[size];
        new Random(42).nextBytes(wav);
        return wav;
    }

    private void enqueueAudio(byte[] wav) {
        String body = """
                {"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"audio/wav","data":"%s"}}]}}]}
                """.formatted(Base64.getEncoder().encodeToString(wav));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody(body));
    }

    @Test
    @DisplayName("15초 분량(약 720KB WAV, base64 약 960KB)의 실제 크기 응답도 받아서 디코딩한다")
    void synthesize_realisticLargeResponse() {
        byte[] wav = wavOfBytes(720 * 1024);
        enqueueAudio(wav);

        SynthesizedAudio audio = synthesizer.synthesize(QUESTION);

        assertThat(audio.data()).isEqualTo(wav);
    }

    @Test
    @DisplayName("30초 분량(약 1.4MB WAV, base64 약 1.9MB)의 응답도 받아서 디코딩한다")
    void synthesize_thirtySecondsResponse() {
        byte[] wav = wavOfBytes(30 * 48 * 1024);
        enqueueAudio(wav);

        SynthesizedAudio audio = synthesizer.synthesize(QUESTION);

        assertThat(audio.data()).isEqualTo(wav);
    }

    @Test
    @DisplayName("응답 크기 한도(4MB)를 넘으면 예외 없이 TTS 실패(SPEECH_SYNTHESIS_FAILED)로 처리한다")
    void synthesize_responseOverLimitFailsGracefully() {
        // WAV 4MB는 base64로 약 5.3MB라 한도를 넘는다.
        enqueueAudio(wavOfBytes(4 * 1024 * 1024));

        assertThatThrownBy(() -> synthesizer.synthesize(QUESTION))
                .isInstanceOf(BusinessException.class)
                .extracting("code").isEqualTo(ErrorCode.SPEECH_SYNTHESIS_FAILED);
    }
}

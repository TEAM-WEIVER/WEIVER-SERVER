package com.weiver.global.speech.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.speech.SpeechTestProperties;
import com.weiver.global.speech.dto.SynthesizedAudio;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeminiSpeechSynthesizerTest {

    private static final String API_KEY = "test-gemini-key";
    private static final byte[] WAV = "RIFF....WAVEfmt fake-wav-bytes".getBytes(StandardCharsets.US_ASCII);
    private static final String QUESTION = "Kafka와 RabbitMQ를 함께 사용한 이유가 무엇인가요?";

    private final ObjectMapper objectMapper = new ObjectMapper();

    private MockWebServer mockWebServer;
    private String baseUrl;

    @BeforeEach
    void setUp() throws IOException {
        mockWebServer = new MockWebServer();
        mockWebServer.start();
        baseUrl = mockWebServer.url("/v1beta").toString();
    }

    @AfterEach
    void tearDown() throws IOException {
        mockWebServer.shutdown();
    }

    private GeminiSpeechSynthesizer synthesizer(String apiKey, Duration timeout) {
        WebClient webClient = WebClient.builder().baseUrl(baseUrl).build();
        return new GeminiSpeechSynthesizer(webClient, SpeechTestProperties.of(baseUrl, apiKey, "", timeout));
    }

    private GeminiSpeechSynthesizer synthesizer() {
        return synthesizer(API_KEY, Duration.ofSeconds(5));
    }

    private MockResponse audioResponse(String mimeType, String base64) {
        String body = """
                {"candidates":[{"content":{"parts":[{"inlineData":{"mimeType":"%s","data":"%s"}}]},"finishReason":"STOP"}],
                 "usageMetadata":{"totalTokenCount":10}}
                """.formatted(mimeType, base64);
        return new MockResponse().setResponseCode(200).setHeader("Content-Type", "application/json").setBody(body);
    }

    private MockResponse wavResponse() {
        return audioResponse("audio/wav", Base64.getEncoder().encodeToString(WAV));
    }

    private void assertFailure(Runnable call) {
        assertThatThrownBy(call::run)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.SPEECH_SYNTHESIS_FAILED);
    }

    @Test
    @DisplayName("성공하면 base64 WAV를 디코딩해 그대로 돌려준다.")
    void synthesize_success() {
        mockWebServer.enqueue(wavResponse());

        SynthesizedAudio result = synthesizer().synthesize(QUESTION);

        assertThat(result.data()).isEqualTo(WAV);
        assertThat(result.mimeType()).isEqualTo("audio/wav");
    }

    @Test
    @DisplayName("요청은 설정된 모델·보이스·스타일로 만들어지고, 키는 URL이 아닌 헤더로 전달되며, 질문 원문은 가공되지 않는다.")
    void synthesize_requestShape() throws Exception {
        mockWebServer.enqueue(wavResponse());

        synthesizer().synthesize(QUESTION);

        RecordedRequest request = mockWebServer.takeRequest(1, TimeUnit.SECONDS);
        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/v1beta/models/" + SpeechTestProperties.TTS_MODEL + ":generateContent");
        assertThat(request.getPath()).doesNotContain(API_KEY).doesNotContain("key=");
        assertThat(request.getHeader("x-goog-api-key")).isEqualTo(API_KEY);
        assertThat(request.getHeader("Content-Type")).startsWith("application/json");

        JsonNode body = objectMapper.readTree(request.getBody().readUtf8());
        JsonNode part = body.at("/contents/0/parts/0");
        assertThat(part.get("text").asText()).isEqualTo(QUESTION);
        assertThat(part.at("/speech_metadata/style").asText()).isEqualTo("calm and professional");
        assertThat(body.at("/contents/0/role").asText()).isEqualTo("user");
        assertThat(body.at("/generationConfig/responseModalities/0").asText()).isEqualTo("AUDIO");
        assertThat(body.at("/generationConfig/speechConfig/voiceConfig/voice").asText()).isEqualTo("Charon");
    }

    @Test
    @DisplayName("mimeType에 파라미터가 붙어도 audio/wav면 성공한다.")
    void synthesize_wavWithParameters() {
        mockWebServer.enqueue(audioResponse("audio/wav; codec=pcm", Base64.getEncoder().encodeToString(WAV)));

        assertThat(synthesizer().synthesize(QUESTION).data()).isEqualTo(WAV);
    }

    @Test
    @DisplayName("mimeType이 audio/wav가 아니면 SPEECH_SYNTHESIS_FAILED 예외가 발생한다.")
    void synthesize_nonWavMimeType() {
        mockWebServer.enqueue(audioResponse("audio/mpeg", Base64.getEncoder().encodeToString(WAV)));

        assertFailure(() -> synthesizer().synthesize(QUESTION));
    }

    @Test
    @DisplayName("응답에 inlineData가 없으면 SPEECH_SYNTHESIS_FAILED 예외가 발생한다.")
    void synthesize_missingInlineData() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"no audio\"}]}}]}"));
        mockWebServer.enqueue(new MockResponse().setResponseCode(200)
                .setHeader("Content-Type", "application/json")
                .setBody("{\"candidates\":[]}"));

        assertFailure(() -> synthesizer().synthesize(QUESTION));
        assertFailure(() -> synthesizer().synthesize(QUESTION));
    }

    @Test
    @DisplayName("base64가 올바르지 않으면 SPEECH_SYNTHESIS_FAILED 예외가 발생한다.")
    void synthesize_invalidBase64() {
        mockWebServer.enqueue(audioResponse("audio/wav", "***not-base64***"));

        assertFailure(() -> synthesizer().synthesize(QUESTION));
    }

    @Test
    @DisplayName("429·4xx·5xx 응답이면 SPEECH_SYNTHESIS_FAILED 예외가 발생하고 재시도하지 않는다.")
    void synthesize_httpErrors() {
        mockWebServer.enqueue(new MockResponse().setResponseCode(429));
        mockWebServer.enqueue(new MockResponse().setResponseCode(400));
        mockWebServer.enqueue(new MockResponse().setResponseCode(403));
        mockWebServer.enqueue(new MockResponse().setResponseCode(500));

        for (int i = 0; i < 4; i++) {
            assertFailure(() -> synthesizer().synthesize(QUESTION));
        }
        assertThat(mockWebServer.getRequestCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("타임아웃이 나면 SPEECH_SYNTHESIS_FAILED 예외가 발생한다.")
    void synthesize_timeout() {
        mockWebServer.enqueue(wavResponse().setHeadersDelay(2, TimeUnit.SECONDS));

        assertFailure(() -> synthesizer(API_KEY, Duration.ofMillis(300)).synthesize(QUESTION));
    }

    @Test
    @DisplayName("API 키가 비어 있으면 외부 호출 없이 SPEECH_SYNTHESIS_FAILED 예외가 발생한다.")
    void synthesize_blankApiKey() {
        assertFailure(() -> synthesizer("", Duration.ofSeconds(5)).synthesize(QUESTION));
        assertThat(mockWebServer.getRequestCount()).isZero();
    }

    @Test
    @DisplayName("입력 텍스트가 비어 있으면 외부 호출 없이 SPEECH_SYNTHESIS_FAILED 예외가 발생한다.")
    void synthesize_blankText() {
        assertFailure(() -> synthesizer().synthesize(" "));
        assertThat(mockWebServer.getRequestCount()).isZero();
    }
}

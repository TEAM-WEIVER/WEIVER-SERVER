package com.weiver.global.speech.config;

import com.weiver.global.speech.FakeSpeechSynthesizer;
import com.weiver.global.speech.FakeSpeechTranscriber;
import com.weiver.global.speech.service.SpeechSynthesizer;
import com.weiver.global.speech.service.SpeechTranscriber;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 없이 SpeechProperties 바인딩과 음성 관련 빈 구성을 검증한다.
 */
class SpeechPropertiesBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withBean(WebClient.Builder.class, WebClient::builder)
            .withUserConfiguration(SpeechConfig.class, SpeechTtsExecutor.class,
                    FakeSpeechTranscriber.class, FakeSpeechSynthesizer.class);

    @Test
    @DisplayName("설정이 없어도 PRD 제안 기본값으로 바인딩된다")
    void bindsDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            SpeechProperties properties = context.getBean(SpeechProperties.class);

            assertThat(properties.stt().baseUrl()).isEqualTo("https://api.groq.com/openai/v1");
            assertThat(properties.stt().model()).isEqualTo("whisper-large-v3");
            assertThat(properties.stt().language()).isEqualTo("ko");
            assertThat(properties.stt().temperature()).isZero();
            assertThat(properties.stt().prompt()).isEmpty();
            assertThat(properties.stt().maxAnswerSeconds()).isEqualTo(120);
            assertThat(properties.stt().durationTolerance()).isEqualTo(Duration.ofSeconds(5));
            assertThat(properties.stt().allowedContentTypes()).containsExactly("audio/webm", "audio/ogg", "audio/mp4");
            assertThat(properties.stt().timeout()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.stt().apiKey()).isNull();

            assertThat(properties.tts().baseUrl()).isEqualTo("https://generativelanguage.googleapis.com/v1beta");
            assertThat(properties.tts().model()).isEqualTo("gemini-3.8-flash-lite-tts");
            assertThat(properties.tts().voice()).isEqualTo("Charon");
            assertThat(properties.tts().style()).isEqualTo("calm and professional");
            assertThat(properties.tts().timeout()).isEqualTo(Duration.ofSeconds(8));
            assertThat(properties.tts().s3Dir()).isEqualTo("interview-tts");
            assertThat(properties.tts().executor().coreSize()).isEqualTo(4);
            assertThat(properties.tts().executor().maxSize()).isEqualTo(8);
            assertThat(properties.tts().executor().queueCapacity()).isEqualTo(50);
            assertThat(properties.tts().executor().maxQueueWait()).isEqualTo(Duration.ofSeconds(5));
        });
    }

    @Test
    @DisplayName("환경변수로 주입한 API 키와 재정의한 값이 바인딩된다")
    void bindsOverrides() {
        contextRunner
                .withPropertyValues(
                        "weiver.speech.stt.api-key=groq-key",
                        "weiver.speech.tts.api-key=gemini-key",
                        "weiver.speech.tts.voice=Kore",
                        "weiver.speech.tts.executor.core-size=2",
                        "weiver.speech.tts.executor.max-queue-wait=3s")
                .run(context -> {
                    SpeechProperties properties = context.getBean(SpeechProperties.class);

                    assertThat(properties.stt().apiKey()).isEqualTo("groq-key");
                    assertThat(properties.tts().apiKey()).isEqualTo("gemini-key");
                    assertThat(properties.tts().voice()).isEqualTo("Kore");
                    assertThat(properties.tts().executor().coreSize()).isEqualTo(2);
                    assertThat(properties.tts().executor().maxQueueWait()).isEqualTo(Duration.ofSeconds(3));
                });
    }

    @Test
    @DisplayName("빈 API 키(환경변수 미설정)로도 컨텍스트가 정상 기동한다")
    void startsWithBlankApiKey() {
        contextRunner
                .withPropertyValues("weiver.speech.stt.api-key=", "weiver.speech.tts.api-key=")
                .run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    @DisplayName("잘못된 설정값(스레드 수 0)이면 기동이 실패한다")
    void failsOnInvalidValue() {
        contextRunner
                .withPropertyValues("weiver.speech.tts.executor.core-size=0")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    @DisplayName("음성 관련 빈이 구성되고 TTS 스레드풀은 Executor 빈을 만들지 않는다")
    void wiresBeans() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(SpeechTtsExecutor.class);
            assertThat(context).hasSingleBean(SpeechTranscriber.class);
            assertThat(context).hasSingleBean(SpeechSynthesizer.class);
            assertThat(context).hasBean(SpeechConfig.GROQ_WEB_CLIENT);
            assertThat(context).hasBean(SpeechConfig.GEMINI_WEB_CLIENT);
            // Spring Boot 기본 applicationTaskExecutor를 없애는 Executor 빈이 생기지 않아야 한다.
            assertThat(context.getBeansOfType(java.util.concurrent.Executor.class)).isEmpty();
        });
    }
}

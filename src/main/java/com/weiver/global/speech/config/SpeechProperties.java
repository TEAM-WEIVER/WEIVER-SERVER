package com.weiver.global.speech.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * STT(Groq)·TTS(Gemini) 연동 설정. API 키는 비어 있을 수 있으며, 비어 있으면 외부 호출 없이 실패 처리한다.
 */
@Validated
@ConfigurationProperties(prefix = "weiver.speech")
public record SpeechProperties(
        @Valid @NotNull @DefaultValue Stt stt,
        @Valid @NotNull @DefaultValue Tts tts
) {

    public record Stt(
            @NotBlank @DefaultValue("https://api.groq.com/openai/v1") String baseUrl,
            String apiKey,
            @NotBlank @DefaultValue("whisper-large-v3") String model,
            @NotBlank @DefaultValue("ko") String language,
            @PositiveOrZero @DefaultValue("0") double temperature,
            // 기술용어 힌트(선택). 무음일 때 힌트가 그대로 출력될 수 있어 기본값은 빈 문자열
            @DefaultValue("") String prompt,
            @Positive @DefaultValue("120") int maxAnswerSeconds,
            @NotNull @DefaultValue("5s") Duration durationTolerance,
            @NotEmpty @DefaultValue({"audio/webm", "audio/ogg", "audio/mp4"}) List<String> allowedContentTypes,
            @NotNull @DefaultValue("30s") Duration timeout
    ) {
    }

    public record Tts(
            @NotBlank @DefaultValue("https://generativelanguage.googleapis.com/v1beta") String baseUrl,
            String apiKey,
            @NotBlank @DefaultValue("gemini-3.8-flash-lite-tts") String model,
            @NotBlank @DefaultValue("Charon") String voice,
            @NotBlank @DefaultValue("calm and professional") String style,
            @NotNull @DefaultValue("8s") Duration timeout,
            @NotBlank @DefaultValue("interview-tts") String s3Dir,
            @Valid @NotNull @DefaultValue Executor executor
    ) {
    }

    public record Executor(
            @Positive @DefaultValue("4") int coreSize,
            @Positive @DefaultValue("8") int maxSize,
            @PositiveOrZero @DefaultValue("50") int queueCapacity
    ) {
    }
}

package com.weiver.global.speech;

import com.weiver.global.speech.config.SpeechProperties;

import java.time.Duration;
import java.util.List;

/**
 * 테스트용 SpeechProperties 조립 헬퍼.
 */
public final class SpeechTestProperties {

    public static final String STT_MODEL = "whisper-large-v3";
    public static final String TTS_MODEL = "gemini-3.8-flash-lite-tts";

    private SpeechTestProperties() {
    }

    public static SpeechProperties of(String baseUrl, String apiKey, String prompt, Duration timeout) {
        SpeechProperties.Stt stt = new SpeechProperties.Stt(
                baseUrl, apiKey, STT_MODEL, "ko", 0, prompt,
                120, Duration.ofSeconds(5), List.of("audio/webm", "audio/ogg", "audio/mp4"), timeout);
        SpeechProperties.Tts tts = new SpeechProperties.Tts(
                baseUrl, apiKey, TTS_MODEL, "Charon", "calm and professional", timeout,
                "interview-tts", new SpeechProperties.Executor(4, 8, 50));
        return new SpeechProperties(stt, tts);
    }
}

package com.weiver.global.speech.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * 음성 공급자 WebClient. API 키는 요청마다 헤더로 붙이므로 여기서는 baseUrl만 설정한다.
 */
@Configuration
@EnableConfigurationProperties(SpeechProperties.class)
public class SpeechConfig {

    public static final String GROQ_WEB_CLIENT = "groqWebClient";
    public static final String GEMINI_WEB_CLIENT = "geminiWebClient";

    /**
     * WebClient 기본 응답 메모리 한도는 256KB라 base64 WAV 응답(15초 분량이 약 960KB)을 읽지 못한다.
     * Gemini TTS는 초당 약 48KB(24kHz 16비트 모노)라 30초 분량이 base64로 약 1.9MB이며, 여유를 두어 4MB로 둔다.
     * 이를 넘는 응답은 읽지 못해 TTS 실패(텍스트만 푸시)로 처리된다.
     */
    static final int GEMINI_MAX_IN_MEMORY_SIZE = 4 * 1024 * 1024;
    /** STT 응답은 텍스트(verbose_json)라 작지만 긴 답변의 세그먼트 정보를 고려해 기본값보다 넉넉히 둔다. */
    static final int GROQ_MAX_IN_MEMORY_SIZE = 4 * 1024 * 1024;

    @Bean(GROQ_WEB_CLIENT)
    public WebClient groqWebClient(WebClient.Builder builder, SpeechProperties properties) {
        return builder.clone()
                .baseUrl(properties.stt().baseUrl())
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(GROQ_MAX_IN_MEMORY_SIZE))
                .build();
    }

    @Bean(GEMINI_WEB_CLIENT)
    public WebClient geminiWebClient(WebClient.Builder builder, SpeechProperties properties) {
        return builder.clone()
                .baseUrl(properties.tts().baseUrl())
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(GEMINI_MAX_IN_MEMORY_SIZE))
                .build();
    }
}

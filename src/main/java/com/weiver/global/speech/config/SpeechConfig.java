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

    @Bean(GROQ_WEB_CLIENT)
    public WebClient groqWebClient(WebClient.Builder builder, SpeechProperties properties) {
        return builder.clone()
                .baseUrl(properties.stt().baseUrl())
                .build();
    }

    @Bean(GEMINI_WEB_CLIENT)
    public WebClient geminiWebClient(WebClient.Builder builder, SpeechProperties properties) {
        return builder.clone()
                .baseUrl(properties.tts().baseUrl())
                .build();
    }
}

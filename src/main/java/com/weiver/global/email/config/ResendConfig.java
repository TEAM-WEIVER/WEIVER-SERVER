package com.weiver.global.email.config;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.web.reactive.function.client.WebClient;

@Profile("prod")
@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(ResendProperties.class)
public class ResendConfig {

    private final ResendProperties resendProperties;

    @Bean
    public WebClient resendWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl(resendProperties.apiUrl())
                .defaultHeader("Authorization", "Bearer " + resendProperties.apiKey())
                .defaultHeader("Content-Type", "application/json")
                .build();
    }
}

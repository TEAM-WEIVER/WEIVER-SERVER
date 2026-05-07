package com.weiver.global.email.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "resend")
public record ResendProperties(
        String apiKey,
        String apiUrl,
        String fromEmail,
        String fromName
) {
}

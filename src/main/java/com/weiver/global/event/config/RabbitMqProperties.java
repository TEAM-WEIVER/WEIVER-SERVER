package com.weiver.global.event.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "weiver.rabbitmq")
public record RabbitMqProperties(
        @NotBlank
        String domainExchange,

        @NotBlank
        String deadLetterExchange,

        @NotBlank
        String springQueue,

        @NotBlank
        String springDlq,

        @NotBlank
        String aiQueue,

        @NotBlank
        String aiDlq
) {
}

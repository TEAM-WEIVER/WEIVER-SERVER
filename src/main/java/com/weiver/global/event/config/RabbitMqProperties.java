package com.weiver.global.event.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "weiver.rabbitmq")
public record RabbitMqProperties(
        String domainExchange,
        String deadLetterExchange,
        String springQueue,
        String springDlq,
        String aiQueue,
        String aiDlq
) {
}

package com.weiver.global.event.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(RabbitMqProperties.class)
public class RabbitPropertiesConfig {
}

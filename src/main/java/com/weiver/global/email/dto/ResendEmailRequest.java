package com.weiver.global.email.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResendEmailRequest(
        String from,
        String to,
        String subject,
        String html,
        String text
) {
}

package com.weiver.global.mail;

public record MailMessage(
        String to,
        String subject,
        String body
) {
}

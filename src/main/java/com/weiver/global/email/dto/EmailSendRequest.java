package com.weiver.global.email.dto;

public record EmailSendRequest(
        String to,
        String subject,
        String textContent,
        String htmlContent
) {
    public static EmailSendRequest ofText(String to, String subject, String textContent) {
        return new EmailSendRequest(to, subject, textContent, null);
    }

    public static EmailSendRequest ofHtml(String to, String subject, String htmlContent) {
        return new EmailSendRequest(to, subject, null, htmlContent);
    }
}

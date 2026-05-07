package com.weiver.global.email.service;

import com.weiver.global.email.dto.EmailSendRequest;

public interface EmailSender {
    void send(EmailSendRequest request);
}

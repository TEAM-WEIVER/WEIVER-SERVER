package com.weiver.global.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("local")
@Component
public class LoggingMailSender implements MailSender {

    @Override
    public void send(MailMessage message) {
        log.info("[Mail] to={}, subject={}, body={}",
                message.to(), message.subject(), message.body());
    }
}

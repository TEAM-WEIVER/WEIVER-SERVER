package com.weiver.global.email;

import com.weiver.global.email.dto.EmailSendRequest;
import com.weiver.global.email.service.EmailSender;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Slf4j
@Profile("local")
@Component
public class LoggingEmailSender implements EmailSender {

    @Override
    public void send(EmailSendRequest request) {
        log.info("[LOCAL EMAIL] to={}, subject={}, text={}, html={}",
                request.to(),
                request.subject(),
                request.textContent(),
                request.htmlContent());
    }
}

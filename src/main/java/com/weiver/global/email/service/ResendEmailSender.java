package com.weiver.global.email.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.email.config.ResendProperties;
import com.weiver.global.email.dto.EmailSendRequest;
import com.weiver.global.email.dto.ResendEmailRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Profile("prod")
@Slf4j
@Component
@RequiredArgsConstructor
public class ResendEmailSender implements EmailSender {

    private final WebClient resendWebClient;
    private final ResendProperties resendProperties;

    @Override
    public void send(EmailSendRequest request) {
        validate(request);

        ResendEmailRequest resendRequest = toResendRequest(request);

        try {
            resendWebClient.post()
                    .uri("/emails")
                    .bodyValue(resendRequest)
                    .retrieve()
                    .onStatus(
                            status -> status.is4xxClientError() || status.is5xxServerError(),
                            response -> response.bodyToMono(String.class)
                                    .defaultIfEmpty("")
                                    .flatMap(errorBody -> {
                                        log.error("[Resend] Email send failed. status={}, body={}" , response.statusCode(), errorBody);
                                        return Mono.error(new BusinessException(ErrorCode.EMAIL_SEND_FAILED));
                                    })
                    )
                    .toBodilessEntity()
                    .block();
            log.info("[Resend] Email sent successfully. to={}, subject={}", request.to(), request.subject());
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected email send error. to={}, subject={}", request.to(), request.subject(), e);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private ResendEmailRequest toResendRequest(EmailSendRequest request) {
        return new ResendEmailRequest(
                formatFrom(),
                request.to(),
                request.subject(),
                request.htmlContent(),
                request.textContent()
        );
    }

    private String formatFrom() {
        return "%s <%s>".formatted(
                resendProperties.fromName(),
                resendProperties.fromEmail()
        );
    }

    private void validate(EmailSendRequest request) {
        if(request == null
                || !hasText(request.to())
                || !hasText(request.subject())
                || (!hasText(request.htmlContent()) && !hasText(request.textContent()))) {
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED);
        }
    }

    private boolean hasText(String text) {
        return text != null && !text.isBlank();
    }
}

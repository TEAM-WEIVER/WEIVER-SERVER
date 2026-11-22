package com.weiver.global.config;

import io.sentry.SentryBaseEvent;
import io.sentry.SentryOptions;
import io.sentry.protocol.Request;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SentryRequestConfig {

    @Bean
    SentryOptions.BeforeSendCallback redactKakaoCallbackFromEvent() {
        return (event, hint) -> {
            redactQuery(event);
            return event;
        };
    }

    @Bean
    SentryOptions.BeforeSendTransactionCallback redactKakaoCallbackFromTransaction() {
        return (transaction, hint) -> {
            redactQuery(transaction);
            return transaction;
        };
    }

    private static void redactQuery(SentryBaseEvent event) {
        Request request = event.getRequest();
        if (request != null && request.getUrl() != null && request.getUrl().endsWith(WhiteListConfig.KAKAO_CALLBACK_PATH)) {
            request.setQueryString(null);
        }
    }
}

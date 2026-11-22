package com.weiver.auth.service;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

class KakaoOAuthPropertiesTest {

    private static final String LOCAL_ORIGIN = "https://weiver.local.weiverai.com:3000";
    private static final String LOCAL_RETURN_URI = LOCAL_ORIGIN + "/auth/kakao/return";

    @Test
    void prodProfileUsesTheOptionalLocalReturnEnvironmentVariable() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=prod", "KAKAO_FRONTEND_RETURN_URI_ROCAL=" + LOCAL_RETURN_URI)
                .run(context -> assertThat(context.getEnvironment().getProperty("auth.kakao.local-frontend-return-uri"))
                        .isEqualTo(LOCAL_RETURN_URI));
    }
}

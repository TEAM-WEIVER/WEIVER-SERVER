package com.weiver.global.speech.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 답변 녹음 업로드 한도가 application.yaml에 명시되어 있는지 확인한다(기본값 1MB/10MB에 의존하지 않기 위함).
 */
class MultipartLimitConfigTest {

    @Test
    @DisplayName("multipart 업로드 한도가 파일 10MB / 요청 11MB로 명시되어 있다")
    void multipartLimitsAreExplicit() {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .run(context -> {
                    String maxFileSize = context.getEnvironment().getProperty("spring.servlet.multipart.max-file-size");
                    String maxRequestSize = context.getEnvironment().getProperty("spring.servlet.multipart.max-request-size");

                    assertThat(DataSize.parse(maxFileSize)).isEqualTo(DataSize.ofMegabytes(10));
                    assertThat(DataSize.parse(maxRequestSize)).isEqualTo(DataSize.ofMegabytes(11));
                });
    }
}

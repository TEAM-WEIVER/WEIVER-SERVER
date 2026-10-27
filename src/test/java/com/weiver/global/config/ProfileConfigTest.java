package com.weiver.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * prod 프로필 설정 파일의 운영 노출 관련 값을 검증한다.
 */
class ProfileConfigTest {

    private static Properties load(String file) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(file));
        return factory.getObject();
    }

    @Test
    @DisplayName("prod 프로필은 오류 응답에 스택트레이스를 넣지 않는다")
    void prodProfile_ErrorResponse_ExcludesStacktrace() {
        // given
        String file = "application-prod.yaml";

        // when
        Properties prod = load(file);

        // then
        assertThat(prod.getProperty("server.error.include-stacktrace")).isEqualTo("never");
    }

    @Test
    @DisplayName("prod 프로필은 actuator를 health만 노출하고 상세 정보를 숨긴다")
    void prodProfile_Actuator_ExposesOnlyHealthWithoutDetails() {
        // given
        String file = "application-prod.yaml";

        // when
        Properties prod = load(file);

        // then
        assertThat(prod.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(prod.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }
}

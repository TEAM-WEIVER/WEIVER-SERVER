package com.weiver.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 프로필 설정 파일의 운영 노출 관련 값을 검증한다.
 * prod는 스택트레이스·actuator 노출을 줄이고, local은 기존 값을 유지한다.
 */
class ProfileConfigTest {

    private static Properties load(String file) {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource(file));
        return factory.getObject();
    }

    @Test
    @DisplayName("prod 프로필은 오류 응답에 스택트레이스를 넣지 않는다")
    void prodDoesNotIncludeStacktrace() {
        Properties prod = load("application-prod.yaml");

        assertThat(prod.getProperty("server.error.include-stacktrace")).isEqualTo("never");
    }

    @Test
    @DisplayName("prod 프로필은 actuator를 health만 노출하고 상세 정보를 숨긴다")
    void prodExposesOnlyHealthWithoutDetails() {
        Properties prod = load("application-prod.yaml");

        assertThat(prod.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health");
        assertThat(prod.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    @DisplayName("local 프로필은 기존 스택트레이스·actuator 설정을 유지한다")
    void localKeepsExistingSettings() {
        Properties local = load("application-local.yaml");

        assertThat(local.getProperty("server.error.include-stacktrace")).isEqualTo("always");
        assertThat(local.getProperty("management.endpoints.web.exposure.include")).isEqualTo("*");
        assertThat(local.getProperty("management.endpoint.health.show-details")).isEqualTo("always");
    }
}

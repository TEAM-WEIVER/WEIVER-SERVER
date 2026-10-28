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
    @DisplayName("prod 프로필은 actuator를 health와 prometheus만 노출하고 health 상세 정보를 숨긴다")
    void prodProfile_Actuator_ExposesOnlyHealthAndPrometheusWithoutDetails() {
        // given
        String file = "application-prod.yaml";

        // when
        Properties prod = load(file);

        // then
        assertThat(prod.getProperty("management.endpoints.web.exposure.include")).isEqualTo("health,prometheus");
        assertThat(prod.getProperty("management.endpoint.health.show-details")).isEqualTo("never");
    }

    @Test
    @DisplayName("prod 프로필은 actuator를 앱 포트와 분리된 관리 포트 8081에 두고 주소는 묶지 않는다")
    void prodProfile_Actuator_UsesSeparateManagementPort() {
        // given
        String file = "application-prod.yaml";

        // when
        Properties prod = load(file);

        // then
        assertThat(prod.getProperty("management.server.port")).isEqualTo("8081");
        assertThat(prod.getProperty("server.port")).isEqualTo("8080");
        assertThat(prod.getProperty("management.server.address")).isNull();
    }

    @Test
    @DisplayName("prod 프로필은 모든 지표에 application과 environment 공통 태그를 붙인다")
    void prodProfile_Metrics_HaveCommonTags() {
        // given
        String file = "application-prod.yaml";

        // when
        Properties prod = load(file);

        // then
        assertThat(prod.getProperty("management.metrics.tags.application"))
                .isEqualTo("${spring.application.name:weiver-backend}");
        assertThat(prod.getProperty("management.metrics.tags.environment")).isEqualTo("prod");
    }
}

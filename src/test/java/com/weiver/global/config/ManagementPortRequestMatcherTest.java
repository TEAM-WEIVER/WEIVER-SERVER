package com.weiver.global.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ManagementPortRequestMatcherTest {

    private static final int MANAGEMENT_PORT = 8081;
    private static final int APPLICATION_PORT = 8080;

    private static MockHttpServletRequest prometheusRequest(int localPort) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/prometheus");
        request.setLocalPort(localPort);
        return request;
    }

    @Test
    @DisplayName("관리 포트(8081)로 들어온 /actuator/prometheus 요청은 매칭한다")
    void matches_RequestOnManagementPort() {
        // given
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(MANAGEMENT_PORT, APPLICATION_PORT);
        MockHttpServletRequest request = prometheusRequest(MANAGEMENT_PORT);

        // when
        boolean matched = matcher.matches(request);

        // then
        assertThat(matched).isTrue();
    }

    @Test
    @DisplayName("앱 포트(8080)로 들어온 /actuator/prometheus 요청은 매칭하지 않는다")
    void doesNotMatch_RequestOnApplicationPort() {
        // given
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(MANAGEMENT_PORT, APPLICATION_PORT);
        MockHttpServletRequest request = prometheusRequest(APPLICATION_PORT);

        // when
        boolean matched = matcher.matches(request);

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("management.server.port 속성이 없으면(-1) 어떤 포트로 온 요청도 매칭하지 않는다")
    void doesNotMatch_WhenManagementPortIsNotConfigured() {
        // given
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(ManagementPortRequestMatcher.NOT_CONFIGURED, APPLICATION_PORT);

        // when
        boolean matchedOnApplicationPort = matcher.matches(prometheusRequest(APPLICATION_PORT));
        boolean matchedOnManagementPort = matcher.matches(prometheusRequest(MANAGEMENT_PORT));

        // then
        assertThat(matchedOnApplicationPort).isFalse();
        assertThat(matchedOnManagementPort).isFalse();
    }

    @Test
    @DisplayName("management.server.port가 0(임의 포트)이면 실제 포트를 알 수 없으므로 매칭하지 않는다")
    void doesNotMatch_WhenManagementPortIsZero() {
        // given
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(0, APPLICATION_PORT);
        MockHttpServletRequest request = prometheusRequest(0);

        // when
        boolean matched = matcher.matches(request);

        // then
        assertThat(matched).isFalse();
    }

    @Test
    @DisplayName("management.server.port가 server.port와 같으면(8080/8080) 앱 포트 요청이므로 매칭하지 않는다")
    void doesNotMatch_WhenManagementPortEqualsServerPort() {
        // given
        ManagementPortRequestMatcher matcher = new ManagementPortRequestMatcher(APPLICATION_PORT, APPLICATION_PORT);
        MockHttpServletRequest request = prometheusRequest(APPLICATION_PORT);

        // when
        boolean matched = matcher.matches(request);

        // then
        assertThat(matched).isFalse();
    }
}

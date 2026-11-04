package com.weiver.global.metrics;

import com.weiver.global.config.CorsConfig;
import com.weiver.global.config.SecurityConfig;
import com.weiver.global.metrics.dto.AiPendingStat;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.global.metrics.type.AiOutcome;
import com.weiver.global.security.csrf.CsrfCookieFilter;
import com.weiver.global.security.handler.SecurityErrorResponseWriter;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.server.ManagementContextAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.web.servlet.ServletManagementContextAutoConfiguration;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.PropertyPlaceholderAutoConfiguration;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.DispatcherServletAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.ServletWebServerFactoryAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.autoconfigure.actuate.observability.AutoConfigureObservability;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

/**
 * 실제 관리 포트의 /actuator/prometheus 출력에 AI 대기·지연 지표가 나오는지 확인한다.
 * ManagementPortSecurityTest와 같은 방식으로 웹·보안·actuator 자동 설정과 지표 컴포넌트만 올린다(DB 없음).
 * 집계 스케줄러는 올리지 않고, 게이지 캐시에 집계 결과를 직접 넣는다.
 */
@AutoConfigureObservability(tracing = false)
@SpringBootTest(
        classes = AiMetricsPrometheusExportTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=health,prometheus",
                "cookie.csrf-token.cookie-name=XSRF-TOKEN",
                "cookie.csrf-token.header-name=X-XSRF-TOKEN",
                "cookie.csrf-token.path=/",
                "cookie.csrf-token.domain=",
                "cookie.csrf-token.secure=false",
                "cookie.csrf-token.same-site=Lax"
        }
)
class AiMetricsPrometheusExportTest {

    private static final int MANAGEMENT_PORT = findFreePort();

    // @SpringBootConfiguration으로 두면 같은 패키지의 다른 슬라이스 테스트가 이 클래스를 설정으로 집어 갈 수 있다.
    @Configuration(proxyBeanMethods = false)
    @ImportAutoConfiguration({
            PropertyPlaceholderAutoConfiguration.class,
            JacksonAutoConfiguration.class,
            HttpMessageConvertersAutoConfiguration.class,
            ServletWebServerFactoryAutoConfiguration.class,
            DispatcherServletAutoConfiguration.class,
            WebMvcAutoConfiguration.class,
            SecurityAutoConfiguration.class,
            SecurityFilterAutoConfiguration.class,
            EndpointAutoConfiguration.class,
            WebEndpointAutoConfiguration.class,
            ManagementContextAutoConfiguration.class,
            ServletManagementContextAutoConfiguration.class,
            MetricsAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class,
            PrometheusMetricsExportAutoConfiguration.class
    })
    @Import({
            CorsConfig.class,
            SecurityConfig.class,
            CsrfCookieFilter.class,
            SecurityErrorResponseWriter.class,
            AiPendingMetrics.class,
            AiRoundtripMetrics.class
    })
    static class TestApplication {
    }

    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry registry) {
        registry.add("management.server.port", () -> MANAGEMENT_PORT);
    }

    @Autowired
    private AiPendingMetrics aiPendingMetrics;

    @Autowired
    private AiRoundtripMetrics aiRoundtripMetrics;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();

    @BeforeEach
    void setUp() throws Exception {
        willAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            chain.doFilter(request, response);
            return null;
        }).given(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("관리 포트 /actuator/prometheus에 flow별 대기 건수·최장 대기 시간 게이지와 요청→결과 지연 타이머가 나온다")
    void prometheus_ExportsAiPendingAndRoundtripMetrics() throws Exception {
        // given
        Map<AiFlow, AiPendingStat> snapshot = new EnumMap<>(AiFlow.class);
        for (AiFlow flow : AiFlow.values()) {
            snapshot.put(flow, new AiPendingStat(0, 0));
        }
        snapshot.put(AiFlow.JD_ANALYSIS, new AiPendingStat(3, 125));
        snapshot.put(AiFlow.INTERVIEW_QUESTION, new AiPendingStat(2, 0));
        aiPendingMetrics.update(snapshot);
        aiRoundtripMetrics.record(AiFlow.PROFILE_SYNC, AiOutcome.COMPLETED, OffsetDateTime.now().minusSeconds(5));
        aiRoundtripMetrics.record(
                AiFlow.INTERVIEW_TRANSCRIPT_SAVE, AiOutcome.FAILED, LocalDateTime.now().minusSeconds(5));

        // when
        String metrics = get(URI.create("http://localhost:" + MANAGEMENT_PORT + "/actuator/prometheus"));
        List<String> aiLines = metrics.lines().filter(line -> line.startsWith("weiver_ai_")).toList();

        // then
        String common = "application=\"weiver-backend\",environment=\"local\"";
        assertThat(metrics).contains("weiver_ai_pending{" + common + ",flow=\"jd_analysis\"} 3.0");
        assertThat(metrics).contains("weiver_ai_pending_oldest_seconds{" + common + ",flow=\"jd_analysis\"} 125.0");
        assertThat(metrics).contains("weiver_ai_pending{" + common + ",flow=\"interview_question\"} 2.0");
        assertThat(metrics).contains(
                "weiver_ai_pending_oldest_seconds{" + common + ",flow=\"interview_question\"} 0.0");
        for (AiFlow flow : AiFlow.values()) {
            assertThat(metrics).contains("weiver_ai_pending{" + common + ",flow=\"" + flow.getTag() + "\"}");
        }
        assertThat(metrics).contains(
                "weiver_ai_roundtrip_seconds_count{" + common + ",flow=\"profile_sync\",outcome=\"completed\"} 1");
        assertThat(metrics).contains("weiver_ai_roundtrip_seconds_count{" + common
                + ",flow=\"interview_transcript_save\",outcome=\"failed\"} 1");
        // 태그는 공통 태그와 flow·outcome뿐이다(사용자·세션 식별자 없음).
        assertThat(aiLines).filteredOn(line -> !line.startsWith("#")).allSatisfy(line -> assertThat(
                line.replaceAll("^[^{]+\\{|}.*$", "").replaceAll("=\"[^\"]*\"", ""))
                .matches("application,environment,flow(,outcome)?"));
    }

    private String get(URI uri) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString())
                .body();
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

package com.weiver.global.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.common.UserRole;
import com.weiver.global.security.csrf.CsrfCookieFilter;
import com.weiver.global.security.handler.SecurityErrorResponseWriter;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.interview.controller.InterviewController;
import com.weiver.interview.service.InterviewAnswerVoiceService;
import com.weiver.interview.service.InterviewFlowService;
import com.weiver.interview.service.InterviewSessionService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.autoconfigure.endpoint.EndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.endpoint.web.WebEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthContributorAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.health.HealthEndpointAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.MetricsAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.metrics.export.prometheus.PrometheusMetricsExportAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.ObservationAutoConfiguration;
import org.springframework.boot.actuate.autoconfigure.observation.web.servlet.WebMvcObservationAutoConfiguration;
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
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.NestedTestConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willAnswer;

/**
 * 앱 포트와 관리 포트를 실제로 띄워, 관리 포트(자식 컨텍스트)에도 SecurityConfig의 보안 체인이 적용되는지와
 * /actuator/prometheus가 관리 포트 요청에만 permitAll인지를 확인한다.
 * DB·Redis·RabbitMQ 없이 웹·보안·actuator 자동 설정만 올린다.
 * application.yaml의 기본 프로필(local)과 application-local.yaml을 그대로 읽는다.
 */
@AutoConfigureObservability(tracing = false)
@SpringBootTest(
        classes = ManagementPortSecurityTest.TestApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "management.endpoints.web.exposure.include=health,prometheus",
                "management.endpoint.health.show-details=never",
                "cookie.csrf-token.cookie-name=XSRF-TOKEN",
                "cookie.csrf-token.header-name=X-XSRF-TOKEN",
                "cookie.csrf-token.path=/",
                "cookie.csrf-token.domain=",
                "cookie.csrf-token.secure=false",
                "cookie.csrf-token.same-site=Lax"
        }
)
class ManagementPortSecurityTest {

    private static final int MANAGEMENT_PORT = findFreePort();
    private static final String TEST_ACCESS_TOKEN = "Bearer test-access-token";

    // @SpringBootConfiguration으로 두면 같은 패키지의 @WebMvcTest가 이 클래스를 설정으로 집어 간다.
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
            HealthContributorAutoConfiguration.class,
            HealthEndpointAutoConfiguration.class,
            ObservationAutoConfiguration.class,
            MetricsAutoConfiguration.class,
            CompositeMeterRegistryAutoConfiguration.class,
            PrometheusMetricsExportAutoConfiguration.class,
            WebMvcObservationAutoConfiguration.class
    })
    @Import({
            CorsConfig.class,
            SecurityConfig.class,
            CsrfCookieFilter.class,
            SecurityErrorResponseWriter.class,
            InterviewController.class
    })
    static class TestApplication {
    }

    @DynamicPropertySource
    static void managementPort(DynamicPropertyRegistry registry) {
        registry.add("management.server.port", () -> MANAGEMENT_PORT);
    }

    @LocalServerPort
    private int applicationPort;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private InterviewSessionService interviewSessionService;

    @MockitoBean
    private InterviewFlowService interviewFlowService;

    @MockitoBean
    private InterviewAnswerVoiceService interviewAnswerVoiceService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .proxy(HttpClient.Builder.NO_PROXY)
            .build();

    /**
     * JWT 검증 대신, 테스트 토큰이 있으면 구직자 인증 정보를 넣고 다음 필터로 넘긴다.
     */
    @BeforeEach
    void setUp() throws Exception {
        stubJwtFilterPassThrough(jwtAuthenticationFilter);
    }

    private static void stubJwtFilterPassThrough(JwtAuthenticationFilter jwtAuthenticationFilter) throws Exception {
        willAnswer(invocation -> {
            ServletRequest request = invocation.getArgument(0);
            ServletResponse response = invocation.getArgument(1);
            FilterChain chain = invocation.getArgument(2);
            if (TEST_ACCESS_TOKEN.equals(((HttpServletRequest) request).getHeader("Authorization"))) {
                AuthenticatedPrincipal principal = new AuthenticatedPrincipal("applicant-public-id", UserRole.APPLICANT);
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                        principal, null, List.of(new SimpleGrantedAuthority("ROLE_APPLICANT"))));
            }
            chain.doFilter(request, response);
            return null;
        }).given(jwtAuthenticationFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("앱 포트로 들어온 비인증 /actuator/prometheus 요청은 401 UNAUTHORIZED로 거절한다")
    void prometheus_OnApplicationPort_WithoutAuthentication_Returns401() throws Exception {
        // given
        URI uri = URI.create("http://localhost:" + applicationPort + "/actuator/prometheus");

        // when
        HttpResponse<String> response = get(uri);

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(errorCodeOf(response)).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("관리 포트로 들어온 /actuator/prometheus 요청은 인증 없이 Prometheus 형식 지표를 반환한다")
    void prometheus_OnManagementPort_WithoutAuthentication_Returns200() throws Exception {
        // given
        URI uri = URI.create("http://localhost:" + MANAGEMENT_PORT + "/actuator/prometheus");

        // when
        HttpResponse<String> response = get(uri);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
                contentType -> assertThat(contentType).startsWith("text/plain"));
        assertThat(response.body()).contains("# TYPE");
    }

    @Test
    @DisplayName("지표에는 설정 파일의 application, environment 공통 태그가 붙는다")
    void prometheus_Metrics_HaveCommonTags() throws Exception {
        // given
        URI uri = URI.create("http://localhost:" + MANAGEMENT_PORT + "/actuator/prometheus");

        // when
        String metrics = get(uri).body();

        // then
        assertThat(metrics).contains("application=\"weiver-backend\"");
        assertThat(metrics).contains("environment=\"local\"");
    }

    @Test
    @DisplayName("관리 포트에도 보안 체인이 적용되어, 허용 목록에 없는 경로는 401 UNAUTHORIZED로 거절한다")
    void managementPort_AppliesSecurityFilterChain_ToNonWhitelistedPath() throws Exception {
        // given
        URI uri = URI.create("http://localhost:" + MANAGEMENT_PORT + "/actuator/info");

        // when
        HttpResponse<String> response = get(uri);

        // then
        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(errorCodeOf(response)).isEqualTo("UNAUTHORIZED");
    }

    @Test
    @DisplayName("관리 포트의 /actuator/health는 인증 없이 상세 정보 없이 UP 상태만 반환한다")
    void health_OnManagementPort_ReturnsStatusOnly() throws Exception {
        // given
        URI uri = URI.create("http://localhost:" + MANAGEMENT_PORT + "/actuator/health");

        // when
        HttpResponse<String> response = get(uri);

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        JsonNode body = objectMapper.readTree(response.body());
        assertThat(body.path("status").asText()).isEqualTo("UP");
        assertThat(body.has("components")).isFalse();
    }

    @Test
    @DisplayName("HTTP 지표의 uri 태그는 실제 ID가 아니라 경로 변수 템플릿으로 남는다")
    void httpServerRequests_UriTag_KeepsPathTemplate() throws Exception {
        // given
        UUID interviewSessionId = UUID.randomUUID();
        String csrfToken = issueCsrfToken();
        HttpRequest analysisRequest = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + applicationPort + "/api/interviews/" + interviewSessionId + "/analysis"))
                .header("Authorization", TEST_ACCESS_TOKEN)
                .header("Cookie", "XSRF-TOKEN=" + csrfToken)
                .header("X-XSRF-TOKEN", csrfToken)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        // when
        HttpResponse<String> analysisResponse = httpClient.send(analysisRequest, HttpResponse.BodyHandlers.ofString());
        String metrics = get(URI.create("http://localhost:" + MANAGEMENT_PORT + "/actuator/prometheus")).body();

        // then
        assertThat(analysisResponse.statusCode()).isEqualTo(200);
        assertThat(metrics).contains("uri=\"/api/interviews/{interviewSessionId}/analysis\"");
        assertThat(metrics).doesNotContain(interviewSessionId.toString());
    }

    private String issueCsrfToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(
                        "http://localhost:" + applicationPort + "/api/interviews/remaining"))
                .header("Authorization", TEST_ACCESS_TOKEN)
                .GET()
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        return response.headers().allValues("Set-Cookie").stream()
                .filter(cookie -> cookie.startsWith("XSRF-TOKEN="))
                .map(cookie -> cookie.substring("XSRF-TOKEN=".length(), cookie.indexOf(';')))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("XSRF-TOKEN 쿠키가 발급되지 않았습니다."));
    }

    /**
     * management.server.port가 환경변수 등으로 server.port와 같은 값으로 덮어써진 경우.
     * Boot는 관리 컨텍스트를 나누지 않고 actuator를 앱 포트에 올리므로, 포트 조건이 매칭하면 지표가 공개된다.
     */
    @Nested
    @NestedTestConfiguration(NestedTestConfiguration.EnclosingConfiguration.OVERRIDE)
    @AutoConfigureObservability(tracing = false)
    @SpringBootTest(
            classes = ManagementPortSecurityTest.TestApplication.class,
            webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT,
            properties = {
                    "management.endpoints.web.exposure.include=health,prometheus",
                    "management.endpoint.health.show-details=never",
                    "cookie.csrf-token.cookie-name=XSRF-TOKEN",
                    "cookie.csrf-token.header-name=X-XSRF-TOKEN",
                    "cookie.csrf-token.path=/",
                    "cookie.csrf-token.domain=",
                    "cookie.csrf-token.secure=false",
                    "cookie.csrf-token.same-site=Lax"
            }
    )
    class WhenManagementPortEqualsServerPort {

        private static final int SHARED_PORT = findFreePort();

        @DynamicPropertySource
        static void samePorts(DynamicPropertyRegistry registry) {
            registry.add("server.port", () -> SHARED_PORT);
            registry.add("management.server.port", () -> SHARED_PORT);
        }

        @Autowired
        private ObjectMapper objectMapper;

        @MockitoBean
        private JwtAuthenticationFilter jwtAuthenticationFilter;

        @MockitoBean
        private InterviewSessionService interviewSessionService;

        @MockitoBean
        private InterviewFlowService interviewFlowService;

        @MockitoBean
        private InterviewAnswerVoiceService interviewAnswerVoiceService;

        @BeforeEach
        void setUpNested() throws Exception {
            stubJwtFilterPassThrough(jwtAuthenticationFilter);
        }

        @Test
        @DisplayName("관리 포트가 앱 포트와 같으면 비인증 /actuator/prometheus 요청은 401 UNAUTHORIZED로 거절한다")
        void prometheus_WhenManagementPortEqualsServerPort_Returns401() throws Exception {
            // given
            URI uri = URI.create("http://localhost:" + SHARED_PORT + "/actuator/prometheus");

            // when
            HttpResponse<String> response = get(uri);

            // then
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(objectMapper.readTree(response.body()).path("errorCode").asText()).isEqualTo("UNAUTHORIZED");
        }
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return httpClient.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private String errorCodeOf(HttpResponse<String> response) throws IOException {
        return objectMapper.readTree(response.body()).path("errorCode").asText();
    }

    private static int findFreePort() {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}

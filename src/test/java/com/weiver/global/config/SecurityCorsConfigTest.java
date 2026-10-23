package com.weiver.global.config;

import com.weiver.auth.controller.ApplicantAuthController;
import com.weiver.auth.service.ApplicantAuthService;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.csrf.CsrfCookieFilter;
import com.weiver.global.security.handler.SecurityErrorResponseWriter;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicantAuthController.class)
@AutoConfigureMockMvc
@Import({
        CorsConfig.class,
        SecurityConfig.class,
        CsrfCookieFilter.class,
        SecurityErrorResponseWriter.class
})
@TestPropertySource(properties = {
        "cookie.csrf-token.cookie-name=XSRF-TOKEN",
        "cookie.csrf-token.header-name=X-XSRF-TOKEN",
        "cookie.csrf-token.path=/",
        "cookie.csrf-token.domain=",
        "cookie.csrf-token.secure=false",
        "cookie.csrf-token.same-site=Lax"
})
class SecurityCorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicantAuthService applicantAuthService;

    @MockitoBean
    private CookieProvider cookieProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @Test
    @DisplayName("로컬 개발 출처 weiver.local.weiverai.com:3000의 이메일 인증 preflight 요청을 허용한다")
    void allowApplicantEmailSendPreflightFromLocalDevOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/applicants/email/send")
                        .header(HttpHeaders.ORIGIN, "https://weiver.local.weiverai.com:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://weiver.local.weiverai.com:3000"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @Test
    @DisplayName("허용 목록에서 제거한 http://localhost:3000의 preflight 요청을 거부한다")
    void rejectPreflightFromRemovedLocalhostOrigin() throws Exception {
        mockMvc.perform(options("/api/auth/applicants/email/send")
                        .header(HttpHeaders.ORIGIN, "http://localhost:3000")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "content-type"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://weiverai.com", "https://www.weiverai.com"})
    void allowProductionPreflightWithAuthAndCsrfHeaders(String origin) throws Exception {
        mockMvc.perform(options("/api/auth/reissue")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization,content-type,x-xsrf-token"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"https://weiverai.com.evil.example", "https://evil.example", "http://weiverai.com", "https://www.piuda.site", "https://weiver.local.piuda.site:3000"})
    void rejectUnregisteredOrigins(String origin) throws Exception {
        mockMvc.perform(options("/api/auth/reissue")
                        .header(HttpHeaders.ORIGIN, origin)
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

}

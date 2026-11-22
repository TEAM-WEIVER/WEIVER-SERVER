package com.weiver.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.auth.dto.KakaoProfile;
import com.weiver.auth.dto.request.KakaoSignupCompleteRequestDTO;
import com.weiver.auth.dto.response.ApplicantLoginMethodsResponseDTO;
import com.weiver.auth.repository.KakaoOAuthTicketRepository;
import com.weiver.auth.service.KakaoApplicantAccountService;
import com.weiver.auth.service.KakaoOAuthClient;
import com.weiver.auth.service.KakaoOAuthProperties;
import com.weiver.auth.service.KakaoOAuthService;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.common.UserRole;
import com.weiver.global.config.CorsConfig;
import com.weiver.global.config.SecurityConfig;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.cookie.CookieProperties;
import com.weiver.global.security.csrf.CsrfCookieFilter;
import com.weiver.global.security.handler.SecurityErrorResponseWriter;
import com.weiver.global.security.jwt.BearerTokenResolver;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtProperties;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.jwt.repository.BlacklistTokenRepository;
import com.weiver.global.security.jwt.repository.TokenVersionRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(KakaoOAuthController.class)
@ExtendWith(OutputCaptureExtension.class)
@Import({CorsConfig.class, SecurityConfig.class, JwtAuthenticationFilter.class, JwtTokenProvider.class,
        BearerTokenResolver.class, SecurityErrorResponseWriter.class, CsrfCookieFilter.class, KakaoOAuthService.class,
        KakaoOAuthTicketRepository.class, CookieProvider.class})
@EnableConfigurationProperties({KakaoOAuthProperties.class, CookieProperties.class, JwtProperties.class})
@TestPropertySource(properties = {
        "cookie.csrf-token.cookie-name=XSRF-TOKEN",
        "cookie.csrf-token.header-name=X-XSRF-TOKEN",
        "cookie.csrf-token.path=/",
        "cookie.csrf-token.same-site=Lax",
        "cookie.refresh-token.name=refreshToken",
        "cookie.refresh-token.path=/",
        "cookie.refresh-token.same-site=Lax",
        "cookie.refresh-token.secure=true",
        "cookie.refresh-token.max-age=3600",
        "jwt.secret=test-secret-key-for-kakao-oauth-controller-verification",
        "jwt.access-token-expiration=600000",
        "jwt.refresh-token-expiration=3600000",
        "auth.kakao.client-id=mock-kakao-app",
        "auth.kakao.client-secret=mock-kakao-secret",
        "auth.kakao.redirect-uri=https://api.weiverai.com/api/auth/applicants/kakao/callback",
        "auth.kakao.frontend-return-uri=https://weiverai.com/auth/kakao/return",
        "auth.kakao.local-frontend-return-uri=https://weiver.local.weiverai.com:3000/auth/kakao/return"
})
class KakaoOAuthControllerTest {

    private static final String BASE = "/api/auth/applicants/kakao";
    private static final String BROWSER = "__Host-WEIVER_KAKAO_BROWSER";
    private static final String RESULT = "__Host-WEIVER_KAKAO_RESULT_";
    private static final String PUBLIC_ID = "applicant-public-id";
    private static final KakaoProfile PROFILE = new KakaoProfile(123456789L, "mock@example.com");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private KakaoOAuthClient client;
    @MockitoBean
    private KakaoApplicantAccountService accounts;
    @MockitoBean
    private BlacklistTokenRepository blacklistTokenRepository;
    @MockitoBean
    private TokenVersionRepository tokenVersionRepository;
    @MockitoBean
    private RedisTemplate<String, String> redisTemplate;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void stubExternalSystems() {
        Map<String, String> redisEntries = new HashMap<>();
        ValueOperations<String, String> values = mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(values);
        doAnswer(call -> {
            redisEntries.put(call.getArgument(0), call.getArgument(1));
            return null;
        }).when(values).set(anyString(), anyString(), any(Duration.class));
        when(values.getAndDelete(anyString())).thenAnswer(call -> redisEntries.remove(call.getArgument(0)));
        when(client.authorizationUri(anyString(), anyBoolean())).thenAnswer(call ->
                URI.create("https://kauth.kakao.com/oauth/authorize?state=" + call.getArgument(0)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/exchange", "/signup/complete", "/link/start", "/link/complete",
            "/reauth/start", "/reauth/complete"})
    void postWithoutCsrfReturnsForbidden(String path) throws Exception {
        mockMvc.perform(post(BASE + path))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/start", "/link/start", "/reauth/start"})
    @DisplayName("로컬 웹에서 카카오 인증을 시작하면 콜백은 로컬 웹으로 복귀한다")
    void callbackReturnsToLocalWebForEachStart(String path) throws Exception {
        // given
        when(accounts.getLoginMethods(PUBLIC_ID)).thenReturn(new ApplicantLoginMethodsResponseDTO(false, true));
        MockHttpServletRequestBuilder request = post(BASE + path)
                .header(HttpHeaders.ORIGIN, "https://weiver.local.weiverai.com:3000")
                .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.APPLICANT))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"password\"}");

        // when
        Start start = start(request);

        // then
        mockMvc.perform(get(BASE + "/callback").param("state", start.state())
                        .param("error", "access_denied").cookie(start.browser()))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION,
                        "https://weiver.local.weiverai.com:3000/auth/kakao/return"));
    }

    @Test
    @DisplayName("운영 웹에서 카카오 로그인을 시작하면 콜백은 기존 복귀 주소로 돌아간다")
    void callbackReturnsToDefaultWebForProductionOrigin() throws Exception {
        // given
        MockHttpServletRequestBuilder request = post(BASE + "/start").header(HttpHeaders.ORIGIN, "https://weiverai.com");

        // when
        Start start = start(request);

        // then
        mockMvc.perform(get(BASE + "/callback").param("state", start.state())
                        .param("error", "access_denied").cookie(start.browser()))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://weiverai.com/auth/kakao/return"));
    }

    @Test
    void linkedApplicantLogsInAndResultCannotBeReused() throws Exception {
        when(client.fetchUser("provider-code")).thenReturn(PROFILE);
        when(accounts.loginIfLinked(PROFILE.kakaoUserId()))
                .thenReturn(Optional.of(new ApplicantLoginResult("weiver-access", "weiver-refresh", UserRole.APPLICANT)));

        Start start = start(post(BASE + "/start").header(HttpHeaders.AUTHORIZATION, "Bearer stale-token"));
        Cookie result = callback(start, "provider-code");

        mockMvc.perform(flowPost("/exchange", start, result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("LOGGED_IN"))
                .andExpect(jsonPath("$.data.accessToken").value("weiver-access"))
                .andExpect(jsonPath("$.data.role").value("APPLICANT"))
                .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                .andExpect(response -> assertThat(response.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                        .anyMatch(header -> header.startsWith("refreshToken=weiver-refresh") && header.contains("HttpOnly")));

        assertInvalidExchange(start, start.browser(), result);
    }

    @Test
    void callbackAndExchangeRequireTheOriginalBrowserWithoutConsumingItsTickets() throws Exception {
        when(client.fetchUser("provider-code")).thenReturn(PROFILE);
        when(accounts.classifySignup(PROFILE.verifiedEmail()))
                .thenReturn(KakaoApplicantAccountService.SignupStatus.SIGNUP_REQUIRED);
        Start start = start(post(BASE + "/start"));
        Cookie otherBrowser = new Cookie(BROWSER, UUID.randomUUID().toString());

        mockMvc.perform(get(BASE + "/callback").param("state", start.state()).param("code", "provider-code").cookie(otherBrowser))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_KAKAO_OAUTH_TICKET"));

        Cookie result = callback(start, "provider-code");
        assertInvalidExchange(start, otherBrowser, result);
        mockMvc.perform(flowPost("/exchange", start, result))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SIGNUP_REQUIRED"))
                .andExpect(jsonPath("$.data.verifiedEmail").value(PROFILE.verifiedEmail()))
                .andExpect(jsonPath("$.data.signupToken").isString());
        mockMvc.perform(get(BASE + "/callback").param("state", start.state()).param("code", "provider-code").cookie(start.browser()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_KAKAO_OAUTH_TICKET"));
    }

    @Test
    void cancellationDoesNotCallTheProviderOrLogin() throws Exception {
        Start start = start(post(BASE + "/start"));
        MvcResult callback = mockMvc.perform(get(BASE + "/callback").param("state", start.state())
                        .param("error", "access_denied").cookie(start.browser()))
                .andExpect(status().isSeeOther()).andReturn();

        mockMvc.perform(flowPost("/exchange", start, callback.getResponse().getCookie(RESULT + start.flowId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("KAKAO_OAUTH_CANCELLED"));
        verifyNoInteractions(accounts);
    }

    @Test
    void providerErrorReturnsSanitizedStatus() throws Exception {
        when(client.fetchUser("provider-code")).thenThrow(new BusinessException(ErrorCode.KAKAO_PROVIDER_ERROR));
        Start start = start(post(BASE + "/start"));

        mockMvc.perform(flowPost("/exchange", start, callback(start, "provider-code")))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.errorCode").value("KAKAO_PROVIDER_ERROR"));
    }

    @Test
    void existingEmailNeverIssuesSignupTicket() throws Exception {
        when(client.fetchUser("provider-code")).thenReturn(PROFILE);
        when(accounts.classifySignup(PROFILE.verifiedEmail()))
                .thenReturn(KakaoApplicantAccountService.SignupStatus.EXISTING_ACCOUNT);
        Start start = start(post(BASE + "/start"));

        mockMvc.perform(flowPost("/exchange", start, callback(start, "provider-code")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("EXISTING_ACCOUNT"))
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andExpect(jsonPath("$.data.signupToken").doesNotExist());
    }

    @Test
    void linkRequiresApplicantAndStartingSessionAndCannotBeExchangedForLogin() throws Exception {
        mockMvc.perform(post(BASE + "/link/start").with(csrf()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));
        mockMvc.perform(post(BASE + "/link/start").with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.COMPANY)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        String original = bearer(UserRole.APPLICANT);
        String changed = bearer(UserRole.APPLICANT);
        when(client.fetchUser("provider-code")).thenReturn(PROFILE);
        Start start = start(post(BASE + "/link/start").header(HttpHeaders.AUTHORIZATION, original)
                .contentType(MediaType.APPLICATION_JSON).content("{\"currentPassword\":\"password\"}"));
        Cookie result = callback(start, "provider-code");
        assertInvalidExchange(start, start.browser(), result);

        mockMvc.perform(flowPost("/link/complete", start, result)
                        .header(HttpHeaders.AUTHORIZATION, changed))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_KAKAO_OAUTH_TICKET"));
        mockMvc.perform(flowPost("/link/complete", start, result)
                        .header(HttpHeaders.AUTHORIZATION, original))
                .andExpect(status().isOk());

        verify(accounts).verifyLinkPassword(PUBLIC_ID, "password");
        verify(accounts).completeLink(PUBLIC_ID, PROFILE.kakaoUserId());
    }

    @Test
    void twoTabsCanOnlyExchangeTheirOwnResult() throws Exception {
        KakaoProfile secondProfile = new KakaoProfile(987654321L, "other@example.com");
        when(client.fetchUser("first-code")).thenReturn(PROFILE);
        when(client.fetchUser("second-code")).thenReturn(secondProfile);
        when(accounts.loginIfLinked(PROFILE.kakaoUserId()))
                .thenReturn(Optional.of(new ApplicantLoginResult("first-access", "first-refresh", UserRole.APPLICANT)));
        when(accounts.loginIfLinked(secondProfile.kakaoUserId()))
                .thenReturn(Optional.of(new ApplicantLoginResult("second-access", "second-refresh", UserRole.APPLICANT)));
        Start first = start(post(BASE + "/start"));
        Start second = start(post(BASE + "/start").cookie(first.browser()));
        Cookie firstResult = callback(first, "first-code");
        Cookie secondResult = callback(second, "second-code");

        // 쿠키 이름이 바뀌어도 두 번째 결과의 증표는 첫 번째 흐름에 사용할 수 없다.
        Cookie swappedResult = new Cookie(firstResult.getName(), secondResult.getValue());
        assertInvalidExchange(first, first.browser(), swappedResult);
        mockMvc.perform(flowPost("/exchange", first, firstResult).cookie(secondResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("first-access"));
        mockMvc.perform(flowPost("/exchange", second, secondResult))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").value("second-access"));
    }

    @Test
    void reauthConfirmsTheKakaoOwnerInTheStartingSession() throws Exception {
        String bearer = bearer(UserRole.APPLICANT);
        when(accounts.getLoginMethods(PUBLIC_ID)).thenReturn(new ApplicantLoginMethodsResponseDTO(false, true));
        when(client.fetchUser("provider-code")).thenReturn(PROFILE);
        when(accounts.findLinkedApplicantPublicId(PROFILE.kakaoUserId())).thenReturn(Optional.of(PUBLIC_ID));
        Start start = start(post(BASE + "/reauth/start").header(HttpHeaders.AUTHORIZATION, bearer));
        Cookie result = callback(start, "provider-code");

        mockMvc.perform(flowPost("/reauth/complete", start, result)
                        .header(HttpHeaders.AUTHORIZATION, bearer(UserRole.APPLICANT)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_KAKAO_OAUTH_TICKET"));
        mockMvc.perform(flowPost("/reauth/complete", start, result).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.reauthToken").isString());
        mockMvc.perform(flowPost("/reauth/complete", start, result).header(HttpHeaders.AUTHORIZATION, bearer))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errorCode").value("INVALID_KAKAO_OAUTH_TICKET"));
    }

    @Test
    void signupIssuesExistingTokensAfterAccountCreationAndConsumesTheSignupTicket() throws Exception {
        when(client.fetchUser("provider-code")).thenReturn(PROFILE);
        when(accounts.classifySignup(PROFILE.verifiedEmail()))
                .thenReturn(KakaoApplicantAccountService.SignupStatus.SIGNUP_REQUIRED);
        when(accounts.completeSignup(eq(PROFILE), any(KakaoSignupCompleteRequestDTO.class))).thenReturn(PUBLIC_ID);
        when(accounts.loginAfterSignup(PROFILE.kakaoUserId(), PUBLIC_ID))
                .thenReturn(new ApplicantLoginResult("signup-access", "signup-refresh", UserRole.APPLICANT));

        Start start = start(post(BASE + "/start"));
        MvcResult exchanged = mockMvc.perform(flowPost("/exchange", start, callback(start, "provider-code")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").doesNotExist())
                .andReturn();
        String signupToken = objectMapper.readTree(exchanged.getResponse().getContentAsString())
                .at("/data/signupToken").asText();
        String signupBody = """
                {"signupToken":"%s","agreements":{"termsOfService":true,"privacyPolicy":true,
                  "individualMemberTerms":true,"aiAnalysisConsent":true,"sensitiveDataConsent":true,"marketingConsent":false}}
                """.formatted(signupToken);
        mockMvc.perform(post(BASE + "/signup/complete").with(csrf()).cookie(start.browser())
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.accessToken").value("signup-access"))
                .andExpect(jsonPath("$.data.role").value("APPLICANT"))
                .andExpect(response -> assertThat(response.getResponse().getHeaders(HttpHeaders.SET_COOKIE))
                        .anyMatch(header -> header.startsWith("refreshToken=signup-refresh") && header.contains("HttpOnly")));
        var order = inOrder(accounts);
        order.verify(accounts).completeSignup(eq(PROFILE), any(KakaoSignupCompleteRequestDTO.class));
        order.verify(accounts).loginAfterSignup(PROFILE.kakaoUserId(), PUBLIC_ID);

        mockMvc.perform(post(BASE + "/signup/complete").with(csrf()).cookie(start.browser())
                        .contentType(MediaType.APPLICATION_JSON).content(signupBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_SIGNUP_TOKEN"));
    }

    @Test
    void callbackDoesNotLogTheProviderQuery(CapturedOutput output) throws Exception {
        String code = "callback-secret-code-fixture";
        when(client.fetchUser(code)).thenReturn(PROFILE);
        Start start = start(post(BASE + "/start"));
        mockMvc.perform(get(BASE + "/callback").queryParam("state", start.state()).queryParam("code", code)
                        .cookie(start.browser()))
                .andExpect(status().isSeeOther());
        assertThat(output.getAll()).contains("uri=" + BASE + "/callback")
                .doesNotContain(code, start.state());
    }

    private Start start(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.with(csrf()))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andReturn();
        Cookie browser = result.getResponse().getCookie(BROWSER);
        assertThat(browser).isNotNull();
        assertThat(browser.isHttpOnly()).isTrue();
        assertThat(browser.getSecure()).isTrue();
        assertThat(browser.getPath()).isEqualTo("/");
        var data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        String url = data.path("authorizationUrl").asText();
        return new Start(browser, UriComponentsBuilder.fromUriString(url).build().getQueryParams().getFirst("state"),
                data.path("flowId").asText());
    }

    private Cookie callback(Start start, String code) throws Exception {
        MvcResult response = mockMvc.perform(get(BASE + "/callback").param("state", start.state()).param("code", code)
                        .cookie(start.browser()).header(HttpHeaders.AUTHORIZATION, "Bearer stale-token"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string(HttpHeaders.LOCATION, "https://weiverai.com/auth/kakao/return"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andReturn();
        Cookie result = response.getResponse().getCookie(RESULT + start.flowId());
        assertThat(result).isNotNull();
        assertThat(result.isHttpOnly()).isTrue();
        assertThat(result.getSecure()).isTrue();
        assertThat(result.getPath()).isEqualTo("/");
        return result;
    }

    private MockHttpServletRequestBuilder flowPost(String path, Start start, Cookie result) {
        return post(BASE + path).with(csrf()).cookie(start.browser(), result)
                .contentType(MediaType.APPLICATION_JSON).content("{\"flowId\":\"" + start.flowId() + "\"}");
    }

    private void assertInvalidExchange(Start start, Cookie browser, Cookie result) throws Exception {
        mockMvc.perform(post(BASE + "/exchange").with(csrf()).cookie(browser, result)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"flowId\":\"" + start.flowId() + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_KAKAO_OAUTH_TICKET"));
    }

    private String bearer(UserRole role) {
        return "Bearer " + jwtTokenProvider.createAccessToken(PUBLIC_ID, role, 0L);
    }

    private record Start(Cookie browser, String state, String flowId) {
    }
}

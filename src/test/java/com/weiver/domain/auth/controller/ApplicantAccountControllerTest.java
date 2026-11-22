package com.weiver.domain.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.auth.controller.ApplicantAccountController;
import com.weiver.auth.controller.KakaoOAuthController;
import com.weiver.auth.dto.request.ApplicantPasswordSetupRequestDTO;
import com.weiver.auth.dto.response.ApplicantLoginMethodsResponseDTO;
import com.weiver.auth.service.KakaoApplicantAccountService;
import com.weiver.auth.service.KakaoOAuthService;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.BearerTokenResolver;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ApplicantAccountController.class)
@AutoConfigureMockMvc(addFilters = false)
class ApplicantAccountControllerTest {

    private static final String BASE_URL = "/api/auth/applicants/me";
    private static final String PUBLIC_ID = "applicant-id";
    private static final String BROWSER_ID = "browser-id";
    private static final String ACCESS_TOKEN = "access-token";
    private static final ApplicantPasswordSetupRequestDTO PASSWORD_SETUP =
            new ApplicantPasswordSetupRequestDTO("reauth-token", "123456", "Secret!25", "Secret!25");

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockitoBean private KakaoApplicantAccountService accountService;
    @MockitoBean private KakaoOAuthService kakaoOAuthService;
    @MockitoBean private CookieProvider cookieProvider;
    @MockitoBean private BearerTokenResolver bearerTokenResolver;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;

    @AfterEach
    void clearAuthentication() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void loginMethods_returnsCurrentApplicantsMethods() throws Exception {
        authenticate();
        when(accountService.getLoginMethods(PUBLIC_ID))
                .thenReturn(new ApplicantLoginMethodsResponseDTO(false, true));

        mockMvc.perform(get(BASE_URL + "/login-methods"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordEnabled").value(false))
                .andExpect(jsonPath("$.data.kakaoLinked").value(true));
    }

    @Test
    void loginMethods_requiresAuthentication() throws Exception {
        mockMvc.perform(get(BASE_URL + "/login-methods"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(accountService);
    }

    @Test
    void sendPasswordSetupCode_usesAuthenticatedApplicant() throws Exception {
        authenticate();

        mockMvc.perform(post(BASE_URL + "/password/setup/send"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(accountService).sendPasswordSetupCode(PUBLIC_ID);
    }

    @Test
    void sendPasswordSetupCode_rejectsAlreadyConfiguredPassword() throws Exception {
        authenticate();
        doThrow(new BusinessException(ErrorCode.PASSWORD_ALREADY_CONFIGURED))
                .when(accountService).sendPasswordSetupCode(PUBLIC_ID);

        mockMvc.perform(post(BASE_URL + "/password/setup/send"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("PASSWORD_ALREADY_CONFIGURED"));
    }

    @Test
    void setupPassword_passesAuthenticatedApplicantAndBrowserToReauthService() throws Exception {
        authenticate();
        when(bearerTokenResolver.resolve(any(HttpServletRequest.class))).thenReturn(ACCESS_TOKEN);

        setupPassword(PASSWORD_SETUP)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"));

        verify(kakaoOAuthService).setupPassword(BROWSER_ID, PUBLIC_ID, ACCESS_TOKEN, PASSWORD_SETUP);
    }

    @Test
    void setupPassword_rejectsInvalidEmailVerificationCode() throws Exception {
        authenticate();
        var request = new ApplicantPasswordSetupRequestDTO("reauth-token", "letters", "Secret!25", "Secret!25");

        setupPassword(request)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_FAILED"));

        verifyNoInteractions(kakaoOAuthService);
    }

    @Test
    void setupPassword_rejectsInvalidKakaoReauth() throws Exception {
        authenticate();
        when(bearerTokenResolver.resolve(any(HttpServletRequest.class))).thenReturn(ACCESS_TOKEN);
        doThrow(new BusinessException(ErrorCode.INVALID_PASSWORD_REAUTH_TOKEN))
                .when(kakaoOAuthService).setupPassword(BROWSER_ID, PUBLIC_ID, ACCESS_TOKEN, PASSWORD_SETUP);

        setupPassword(PASSWORD_SETUP)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_PASSWORD_REAUTH_TOKEN"));
    }

    private ResultActions setupPassword(ApplicantPasswordSetupRequestDTO request) throws Exception {
        return mockMvc.perform(patch(BASE_URL + "/password/setup")
                .cookie(new Cookie(KakaoOAuthController.BROWSER_COOKIE_NAME, BROWSER_ID))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + ACCESS_TOKEN)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsBytes(request)));
    }

    private void authenticate() {
        var principal = new AuthenticatedPrincipal(PUBLIC_ID, UserRole.APPLICANT);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(principal, null, List.of()));
    }
}

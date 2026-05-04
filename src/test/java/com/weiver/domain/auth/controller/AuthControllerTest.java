package com.weiver.domain.auth.controller;

import com.github.dockerjava.zerodep.shaded.org.apache.hc.client5.http.protocol.RequestAddCookies;
import com.weiver.auth.controller.AuthController;
import com.weiver.auth.service.AuthService;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.cookie.RefreshTokenCookieResolver;
import com.weiver.global.security.jwt.BearerTokenResolver;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseCookie;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;
    @MockitoBean
    private CookieProvider cookieProvider;
    @MockitoBean
    private BearerTokenResolver bearerTokenResolver;
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean
    private RefreshTokenCookieResolver refreshTokenCookieResolver;

    @Test
    @DisplayName("로그아웃 성공 시 AuthService를 호출하고 RefreshToken 쿠키를 만료한다.")
    public void logoutSuccess() throws Exception {
        // given
        String accessToken = "validAccessToken";

        ResponseCookie expiredCookie = ResponseCookie.from("refreshToken", "")
                .path("/")
                .httpOnly(true)
                .secure(false)
                .sameSite("Lax")
                .maxAge(0)
                .build();

        when(bearerTokenResolver.resolve("Bearer " + accessToken)).thenReturn(accessToken);
        when(cookieProvider.createExpiredRefreshTokenCookie()).thenReturn(expiredCookie);

        // when & then
        mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.message").value("로그아웃에 성공했습니다."));

        verify(authService).logout(accessToken);
        verify(cookieProvider).createExpiredRefreshTokenCookie();
    }

    @Test
    @DisplayName("Authorization Header가 Bearer 형식이 아니면 401 INVALID_TOKEN 반환")
    public void logoutInvalidToken() throws Exception {
        // given
        when(bearerTokenResolver.resolve("invalidToken"))
                .thenThrow(new BusinessException(ErrorCode.INVALID_TOKEN));

        // when & then
        mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "invalidToken")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("재사용 탐지 발생 시 RefreshToken Cookie 만료 헤더가 내려감")
    public void removedRefreshTokenCookie() throws Exception {
        // given
        String refreshToken = "reusedRefreshToken";
        Cookie requestCookie = new Cookie("refreshToken", refreshToken);

        when(refreshTokenCookieResolver.resolve(ArgumentMatchers.any())).thenReturn(refreshToken);
        when(authService.reissueToken(refreshToken))
                .thenThrow(new BusinessException(ErrorCode.TOKEN_REUSE_DETECTED));
        when(cookieProvider.createExpiredRefreshTokenCookie()).thenReturn(
                ResponseCookie.from("refreshToken", "")
                        .path("/")
                        .httpOnly(true)
                        .secure(false)
                        .sameSite("Lax")
                        .maxAge(0)
                        .build()
        );

        // when & then
        mockMvc.perform(post("/api/auth/reissue")
                .cookie(requestCookie)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("refreshToken=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")))
                .andExpect(jsonPath("$.errorCode").value("TOKEN_REUSE_DETECTED"));

        verify(authService).reissueToken(refreshToken);
        verify(cookieProvider).createExpiredRefreshTokenCookie();
    }
}
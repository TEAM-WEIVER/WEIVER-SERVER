package com.weiver.auth.controller;

import com.weiver.auth.dto.response.ReissueResponseDTO;
import com.weiver.auth.service.AuthService;
import com.weiver.auth.service.dto.TokenReissueResult;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.cookie.RefreshTokenCookieResolver;
import com.weiver.global.security.jwt.BearerTokenResolver;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieProvider cookieProvider;
    private final BearerTokenResolver bearerTokenResolver;
    private final RefreshTokenCookieResolver refreshTokenCookieResolver;

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String authorizationHeader,

            HttpServletResponse response
    ) {
        String accessToken = bearerTokenResolver.resolve(authorizationHeader);

        authService.logout(accessToken);

        ResponseCookie expiredRefreshTokenCookie = cookieProvider.createExpiredRefreshTokenCookie();

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                expiredRefreshTokenCookie.toString()
        );

        return ResponseEntity.ok(ApiResponse.success("로그아웃에 성공했습니다."));
    }

    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponseDTO>> reissue(
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String refreshToken = refreshTokenCookieResolver.resolve(request);
        TokenReissueResult tokenReissueResult = authService.reissueToken(refreshToken);

        ResponseCookie refreshTokenCookie = cookieProvider.createRefreshTokenCookie(tokenReissueResult.refreshToken());

        response.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookie.toString()
        );

        return ResponseEntity.ok(ApiResponse.success(
                200,
                new ReissueResponseDTO(tokenReissueResult.accessToken()),
                "토큰 재발급에 성공했습니다." ));
    }
}

package com.weiver.auth.controller;

import com.weiver.auth.dto.response.CsrfTokenResponse;
import com.weiver.auth.dto.response.ReissueResponseDTO;
import com.weiver.auth.service.AuthService;
import com.weiver.auth.service.dto.TokenReissueResult;
import com.weiver.global.common.ApiResponse;

import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.cookie.RefreshTokenCookieResolver;
import com.weiver.global.security.jwt.BearerTokenResolver;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@Tag(name = "통합 계정 관리에 사용되는 인증(auth) API", description = "구직자, 기업 관리자가 사용하는 로그아웃, JWT 토큰 재발급, CSRF 토큰 발급을 처리하는 인증 API 입니다.")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CookieProvider cookieProvider;
    private final BearerTokenResolver bearerTokenResolver;
    private final RefreshTokenCookieResolver refreshTokenCookieResolver;

    @Operation(summary = "로그아웃")
    @SecurityRequirement(name = "bearerAuth")
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(
            @Parameter(hidden = true)
            @RequestHeader(HttpHeaders.AUTHORIZATION)
            String authorizationHeader,

            @Parameter(hidden = true)
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

    @Operation(
            summary = "AccessToken 재발급",
            description = "요청 Cookie에 포함된 RefreshToken을 이용하여 AccessToken을 재발급합니다.<br>" +
                    "재발급 성공 시 새로운 RefreshToken은 Set-Cookie Header로 내려가며, AccessToken은 응답 Body로 반환됩니다."
    )
    @PostMapping("/reissue")
    public ResponseEntity<ApiResponse<ReissueResponseDTO>> reissue(
            @Parameter(hidden = true)
            HttpServletRequest request,

            @Parameter(hidden = true)
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

    @Operation(
            summary = "CSRF 토큰 발급",
            description = "CSRF 보호가 필요한 요청에서 사용할 CSRF 토큰을 발급합니다.<br>" +
                    "서버는 CSRF 토큰을 XSRF-TOKEN Cookie로 내려줍니다.<br>" +
                    "이후 상태 변경 요청에서는 XSRF-TOKEN Cookie와 함께 X-XSRF-TOKEN Header가 전달되어야 합니다.(공격자가 임의로 넣기 어려운 커스텀 Header에도 같은 토큰이 들어왔는지 확인하는 작업을 함.)<br>" +
                    "Axios 등 HTTP 클라이언트 설정에 따라 Cookie 값을 Header로 자동 반영할 수 있습니다."
    )
    @GetMapping("/csrf")
    public ResponseEntity<ApiResponse<CsrfTokenResponse>> csrf(
            @Parameter(hidden = true)
            CsrfToken csrfToken
    ) {
        return ResponseEntity.ok(ApiResponse.success(
                200,
                new CsrfTokenResponse(csrfToken.getToken()),
                "CSRF 토큰 발급에 성공했습니다."
        ));
    }
}

package com.weiver.auth.controller;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.weiver.auth.dto.request.ApplicantPasswordVerifyRequestDTO;
import com.weiver.auth.dto.request.KakaoSignupCompleteRequestDTO;
import com.weiver.auth.dto.response.ApplicantPasswordVerifyResponseDTO;
import com.weiver.auth.dto.response.ApplicantSignupResponseDTO;
import com.weiver.auth.service.KakaoOAuthService;
import com.weiver.auth.service.KakaoOAuthService.AuthorizationStart;
import com.weiver.auth.service.KakaoOAuthService.CallbackReturn;
import com.weiver.auth.service.KakaoOAuthService.ExchangeStatus;
import com.weiver.auth.service.KakaoOAuthService.LoginExchange;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.common.UserRole;
import com.weiver.global.config.WhiteListConfig;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.BearerTokenResolver;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.WebUtils;

import java.net.URI;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
@RequestMapping(WhiteListConfig.KAKAO_AUTH_PATH)
public class KakaoOAuthController {

    public static final String BROWSER_COOKIE_NAME = "__Host-WEIVER_KAKAO_BROWSER";
    private static final String RESULT_COOKIE_PREFIX = "__Host-WEIVER_KAKAO_RESULT_";

    private final KakaoOAuthService kakaoOAuthService;
    private final CookieProvider cookieProvider;
    private final BearerTokenResolver bearerTokenResolver;

    @PostMapping("/start")
    public ResponseEntity<ApiResponse<StartResponse>> start(
            @CookieValue(name = BROWSER_COOKIE_NAME, required = false) String browserId
    ) {
        return startResponse(kakaoOAuthService.startLogin(browserId));
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @CookieValue(name = BROWSER_COOKIE_NAME, required = false) String browserId,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "error", required = false) String providerError
    ) {
        CallbackReturn result = kakaoOAuthService.callback(browserId, state, code, providerError);
        return ResponseEntity.status(HttpStatus.SEE_OTHER)
                .location(result.returnUri())
                .cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .header(HttpHeaders.SET_COOKIE, oauthCookie(resultCookieName(result.flowId()), result.resultToken(), KakaoOAuthService.RESULT_TTL))
                .build();
    }

    @PostMapping("/exchange")
    public ResponseEntity<ApiResponse<ExchangeResponse>> exchange(
            @CookieValue(name = BROWSER_COOKIE_NAME, required = false) String browserId,
            @Valid @RequestBody FlowRequest flow,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String resultToken = readResultCookie(flow.flowId(), request);
        LoginExchange result = kakaoOAuthService.exchange(browserId, flow.flowId(), resultToken);
        expireResultCookie(flow.flowId(), response);
        ApplicantLoginResult login = result.login();
        if (login != null) {
            writeRefreshCookie(response, login);
        }
        ExchangeResponse body = new ExchangeResponse(result.status(), result.signupToken(), result.verifiedEmail(),
                login == null ? null : login.accessToken(), login == null ? null : login.role());
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(ApiResponse.success(body));
    }

    @PostMapping("/signup/complete")
    public ResponseEntity<ApiResponse<ApplicantSignupResponseDTO>> completeSignup(
            @CookieValue(name = BROWSER_COOKIE_NAME, required = false) String browserId,
            @Valid @RequestBody KakaoSignupCompleteRequestDTO request,
            HttpServletResponse response
    ) {
        ApplicantLoginResult login = kakaoOAuthService.completeSignup(browserId, request);
        writeRefreshCookie(response, login);
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore())
                .body(ApiResponse.created(new ApplicantSignupResponseDTO(login.role(), login.accessToken()),
                        "회원가입에 성공했습니다."));
    }

    @PostMapping("/link/start")
    public ResponseEntity<ApiResponse<StartResponse>> startLink(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @CookieValue(name = BROWSER_COOKIE_NAME, required = false) String browserId,
            @Valid @RequestBody ApplicantPasswordVerifyRequestDTO request,
            HttpServletRequest httpRequest
    ) {
        return startResponse(kakaoOAuthService.startLink(browserId, applicantPublicId(principal),
                bearerTokenResolver.resolve(httpRequest), request.currentPassword()));
    }

    @PostMapping("/link/complete")
    public ResponseEntity<ApiResponse<Void>> completeLink(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @CookieValue(name = BROWSER_COOKIE_NAME, required = false) String browserId,
            @Valid @RequestBody FlowRequest flow,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String resultToken = readResultCookie(flow.flowId(), request);
        kakaoOAuthService.completeLink(browserId, applicantPublicId(principal), bearerTokenResolver.resolve(request), flow.flowId(), resultToken);
        expireResultCookie(flow.flowId(), response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success("카카오 계정을 연결했습니다."));
    }

    @PostMapping("/reauth/start")
    public ResponseEntity<ApiResponse<StartResponse>> startReauth(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @CookieValue(name = BROWSER_COOKIE_NAME, required = false) String browserId,
            HttpServletRequest request
    ) {
        return startResponse(kakaoOAuthService.startReauth(browserId, applicantPublicId(principal), bearerTokenResolver.resolve(request)));
    }

    @PostMapping("/reauth/complete")
    public ResponseEntity<ApiResponse<ApplicantPasswordVerifyResponseDTO>> completeReauth(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            @CookieValue(name = BROWSER_COOKIE_NAME, required = false) String browserId,
            @Valid @RequestBody FlowRequest flow,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String resultToken = readResultCookie(flow.flowId(), request);
        String reauthToken = kakaoOAuthService.completeReauth(browserId, applicantPublicId(principal),
                bearerTokenResolver.resolve(request), flow.flowId(), resultToken);
        expireResultCookie(flow.flowId(), response);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.success(new ApplicantPasswordVerifyResponseDTO(reauthToken)));
    }

    private ResponseEntity<ApiResponse<StartResponse>> startResponse(AuthorizationStart start) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header(HttpHeaders.SET_COOKIE, oauthCookie(BROWSER_COOKIE_NAME, start.browserId(), KakaoOAuthService.BROWSER_TTL))
                .body(ApiResponse.success(new StartResponse(start.authorizationUri(), start.flowId())));
    }

    private static String applicantPublicId(AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        if (principal.role() != UserRole.APPLICANT) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        return principal.publicId();
    }

    private void writeRefreshCookie(HttpServletResponse response, ApplicantLoginResult result) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookieProvider.createRefreshTokenCookie(result.refreshToken()).toString());
    }

    private static String readResultCookie(String flowId, HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, resultCookieName(flowId));
        return cookie == null ? null : cookie.getValue();
    }

    private static void expireResultCookie(String flowId, HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, oauthCookie(resultCookieName(flowId), "", Duration.ZERO));
    }

    private static String resultCookieName(String flowId) {
        return RESULT_COOKIE_PREFIX + flowId;
    }

    private static String oauthCookie(String name, String value, Duration duration) {
        return ResponseCookie.from(name, value)
                .path("/")
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .maxAge(duration)
                .build().toString();
    }

    public record StartResponse(URI authorizationUrl, String flowId) {
    }

    public record FlowRequest(@NotBlank @Pattern(regexp = "[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}") String flowId) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ExchangeResponse(ExchangeStatus status, String signupToken, String verifiedEmail, String accessToken, UserRole role) {
    }
}

package com.weiver.auth.controller;

import com.weiver.auth.dto.request.ApplicantPasswordSetupRequestDTO;
import com.weiver.auth.dto.response.ApplicantLoginMethodsResponseDTO;
import com.weiver.auth.service.KakaoApplicantAccountService;
import com.weiver.auth.service.KakaoOAuthService;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.jwt.BearerTokenResolver;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth/applicants/me")
@RequiredArgsConstructor
@Tag(name = "Applicant Account", description = "구직자 로그인 수단 관리 API")
@SecurityRequirement(name = "bearerAuth")
public class ApplicantAccountController {

    private final KakaoApplicantAccountService accountService;
    private final KakaoOAuthService kakaoOAuthService;
    private final BearerTokenResolver bearerTokenResolver;

    @Operation(summary = "내 로그인 수단 조회")
    @GetMapping("/login-methods")
    public ResponseEntity<ApiResponse<ApplicantLoginMethodsResponseDTO>> getLoginMethods(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return ResponseEntity.ok(ApiResponse.success(accountService.getLoginMethods(principal.publicId())));
    }

    @Operation(summary = "최초 비밀번호 설정 인증번호 발송", description = "현재 계정의 연락 이메일로 인증번호를 보냅니다.")
    @PostMapping("/password/setup/send")
    public ResponseEntity<ApiResponse<Void>> sendPasswordSetupCode(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        accountService.sendPasswordSetupCode(principal.publicId());
        return ResponseEntity.ok(ApiResponse.success(200, null, "이메일 인증번호 전송에 성공했습니다."));
    }

    @Operation(
            summary = "이메일 로그인 비밀번호 최초 설정",
            description = "카카오 재인증 증표와 이메일 인증번호가 필요합니다. 설정 후 다시 로그인합니다."
    )
    @PatchMapping("/password/setup")
    public ResponseEntity<ApiResponse<Void>> setupPassword(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal,
            @CookieValue(name = KakaoOAuthController.BROWSER_COOKIE_NAME, required = false)
            @Parameter(hidden = true) String browserId,
            @RequestBody @Valid ApplicantPasswordSetupRequestDTO request,
            HttpServletRequest httpRequest) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        kakaoOAuthService.setupPassword(browserId, principal.publicId(), bearerTokenResolver.resolve(httpRequest), request);
        return ResponseEntity.ok(ApiResponse.success(200, null, "이메일 로그인 비밀번호 설정에 성공했습니다."));
    }
}

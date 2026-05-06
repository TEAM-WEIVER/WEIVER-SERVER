package com.weiver.auth.controller;

import com.weiver.auth.dto.request.ApplicantEmailSendRequestDTO;
import com.weiver.auth.dto.request.ApplicantEmailVerifyRequestDTO;
import com.weiver.auth.dto.request.ApplicantLoginRequestDTO;
import com.weiver.auth.dto.request.ApplicantSignupRequestDTO;
import com.weiver.auth.dto.response.ApplicantEmailVerifyResponseDTO;
import com.weiver.auth.dto.response.ApplicantLoginResponseDTO;
import com.weiver.auth.dto.response.ApplicantSignupResponseDTO;
import com.weiver.auth.service.ApplicantAuthService;
import com.weiver.auth.service.dto.ApplicantLoginResult;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.global.security.cookie.CookieProvider;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth/applicants")
@RequiredArgsConstructor
public class ApplicantAuthController {

    private final ApplicantAuthService applicantAuthService;
    private final CookieProvider cookieProvider;

    @PostMapping("/email/send")
    public ResponseEntity<ApiResponse<Void>> sendEmailCode(
            @RequestBody
            @Valid
            ApplicantEmailSendRequestDTO requestDTO
    ) {
        applicantAuthService.sendEmailCode(requestDTO);

        return ResponseEntity.ok(ApiResponse.success(200, null, "이메일 인증번호 전송에 성공했습니다."));
    }

    @PostMapping("/email/verify")
    public ResponseEntity<ApiResponse<ApplicantEmailVerifyResponseDTO>> verifyEmailCode(
            @RequestBody
            @Valid
            ApplicantEmailVerifyRequestDTO requestDTO
    ) {
        ApplicantEmailVerifyResponseDTO responseDTO = applicantAuthService.verifyEmailCode(requestDTO);

        return ResponseEntity.ok(ApiResponse.success(200, responseDTO, "이메일 인증번호 확인에 성공했습니다."));
    }

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<ApplicantSignupResponseDTO>> signup(
            @RequestBody
            @Valid
            ApplicantSignupRequestDTO requestDTO
    ) {
        ApplicantSignupResponseDTO responseDTO = applicantAuthService.signup(requestDTO);

        return ResponseEntity.ok(ApiResponse.success(201, responseDTO, "회원가입에 성공했습니다."));
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<ApplicantLoginResponseDTO>> login(
            @RequestBody
            @Valid
            ApplicantLoginRequestDTO requestDTO,
            HttpServletResponse httpServletResponse
    ) {
        ApplicantLoginResult loginResult = applicantAuthService.login(requestDTO);

        ResponseCookie refreshTokenCookie = cookieProvider.createRefreshTokenCookie(loginResult.refreshToken());

        httpServletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                refreshTokenCookie.toString()
        );

        ApplicantLoginResponseDTO responseDTO = new ApplicantLoginResponseDTO(loginResult.accessToken(), loginResult.role());

        return ResponseEntity.ok(ApiResponse.success(200, responseDTO, "로그인에 성공했습니다."));

    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<Void>> withdraw(
            @AuthenticationPrincipal AuthenticatedPrincipal principal,
            HttpServletResponse httpServletResponse
    ) {
        applicantAuthService.withdraw(principal.publicId());

        ResponseCookie expiredRefreshTokenCookie = cookieProvider.createExpiredRefreshTokenCookie();

        httpServletResponse.addHeader(
                HttpHeaders.SET_COOKIE,
                expiredRefreshTokenCookie.toString()
        );

        return ResponseEntity.ok(ApiResponse.success(200, null, "회원탈퇴에 성공했습니다."));
    }
}

package com.weiver.auth.controller;

import com.weiver.auth.dto.request.CompanyLoginRequestDTO;
import com.weiver.auth.dto.response.CompanyLoginResponseDTO;
import com.weiver.auth.service.CompanyAuthService;
import com.weiver.auth.service.dto.CompanyLoginResult;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.security.cookie.CookieProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "기업 담당자 인증 API", description = "기업 담당자 로그인을 처리하는 API입니다.")
@RestController
@RequestMapping("/api/auth/companies")
@RequiredArgsConstructor
public class CompanyAuthController {

    private final CompanyAuthService companyAuthService;
    private final CookieProvider cookieProvider;

    @Operation(
            summary = "기업 로그인",
            description = "기업 회원 로그인을 시도합니다."
    )
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<CompanyLoginResponseDTO>> login(
            @RequestBody
            @Valid
            CompanyLoginRequestDTO requestDTO,

            @Parameter(hidden = true)
            HttpServletResponse response
    ) {
        CompanyLoginResult loginResult = companyAuthService.login(requestDTO);

        ResponseCookie refreshTokenCookie = cookieProvider.createRefreshTokenCookie(loginResult.refreshToken());

        response.setHeader(HttpHeaders.SET_COOKIE, refreshTokenCookie.toString());

        CompanyLoginResponseDTO responseDTO = new CompanyLoginResponseDTO(loginResult.accessToken(), loginResult.role());

        return ResponseEntity.ok(ApiResponse.success(200, responseDTO, "로그인에 성공했습니다."));
    }
}

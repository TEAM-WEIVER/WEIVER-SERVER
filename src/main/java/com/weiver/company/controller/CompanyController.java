package com.weiver.company.controller;

import com.weiver.company.dto.response.CompanyInfoResponseDTO;
import com.weiver.company.service.CompanyService;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "기업 정보 API", description = "기업 정보 조회 API")
@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;

    @Operation(
            summary = "기업 정보 조회",
            description = "현재 로그인한 기업 담당자의 기업 정보를 조회합니다."
    )
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CompanyInfoResponseDTO>> getMyCompanyInfo(
            @Parameter(hidden = true)
            @AuthenticationPrincipal
            AuthenticatedPrincipal principal
    ) {
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        CompanyInfoResponseDTO responseDTO = companyService.getMyCompanyInfo(principal.publicId(), principal.role());

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }
}

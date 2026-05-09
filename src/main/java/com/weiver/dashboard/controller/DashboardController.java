package com.weiver.dashboard.controller;

import com.weiver.dashboard.dto.response.CompanyDashboardResponseDTO;
import com.weiver.dashboard.service.DashboardService;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "대시보드 API", description = "기업 대시보드 관련 API")
@RestController
@RequestMapping("/api/dashboards")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @Operation(summary = "기업 정보 카드 조회", description = "대시보드 상단에 표시되는 기업의 기본 정보를 조회합니다.")
    @GetMapping("/company")
    public ResponseEntity<ApiResponse<CompanyDashboardResponseDTO>> getCompanyInfo(
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        CompanyDashboardResponseDTO responseDTO = dashboardService.getCompanyInfo(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }
}

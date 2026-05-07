package com.weiver.dashboard.controller;

import com.weiver.dashboard.dto.response.CompanyDashboardResponseDTO;
import com.weiver.dashboard.service.DashboardService;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-postings")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<CompanyDashboardResponseDTO>> getCompanyInfo(
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {
        if(principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        CompanyDashboardResponseDTO responseDTO = dashboardService.getCompanyInfo(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }
}

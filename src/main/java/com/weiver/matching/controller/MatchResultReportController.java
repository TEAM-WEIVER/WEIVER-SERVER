package com.weiver.matching.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.matching.dto.response.ApplicantCardResponseDTO;
import com.weiver.matching.service.MatchResultReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/job-postings/{jdId}/applicants/{applicantPublicId}/reports")
@RequiredArgsConstructor
public class MatchResultReportController {

    private final MatchResultReportService matchResultReportService;

    @GetMapping("/card-summary")
    public ResponseEntity<ApiResponse<ApplicantCardResponseDTO>> getCardSummary(
            @PathVariable("jdId") Long jdId,
            @PathVariable("applicantPublicId") String applicantPublicId,
            @AuthenticationPrincipal AuthenticatedPrincipal principal){
        if (principal == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        ApplicantCardResponseDTO responseDTO = matchResultReportService.getCardSummary(jdId, applicantPublicId, principal.publicId());
        return ResponseEntity.ok(ApiResponse.success(responseDTO));
    }
}

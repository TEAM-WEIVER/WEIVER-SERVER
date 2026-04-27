package com.weiver.portfolio.controller;

import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;
import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.portfolio.dto.request.PortfolioRequestDTO;
import com.weiver.portfolio.dto.request.PortfolioUpdateRequestDTO;
import com.weiver.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/portfolios")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService portfolioService;

    @PostMapping
    public ResponseEntity<ApiResponse<Void>> savePortfolio(
            @RequestPart(value = "requestDTO") @Valid PortfolioRequestDTO requestDTO,
            @RequestPart(value = "portfolio", required = false) MultipartFile portfolio,
            Principal principal){
        Long applicantId = extractedId(principal);

        portfolioService.savePortfolio(requestDTO, portfolio, applicantId);

        return ResponseEntity.ok(ApiResponse.success("포트폴리오 저장 완료됐습니다."));
    }

    @PatchMapping("/{portfolioId}")
    public ResponseEntity<ApiResponse<Void>> updatePortfolio(
            @RequestPart(value = "requestDTO") @Valid PortfolioUpdateRequestDTO requestDTO,
            @RequestPart(value = "portfolio", required = false) MultipartFile portfolio,
            @PathVariable Long portfolioId,
            Principal principal){
        Long applicantId = extractedId(principal);

        portfolioService.updatePortfolio(requestDTO, portfolio, applicantId, portfolioId);
        
        return ResponseEntity.ok(ApiResponse.success("포트폴리오 수정 완료됐습니다."));
    }

    /**
     * 편의 메소드
     * */
    private static Long extractedId(Principal principal) {
        if(principal == null){
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        return Long.parseLong(principal.getName());
    }
}

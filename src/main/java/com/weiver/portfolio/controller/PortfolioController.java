package com.weiver.portfolio.controller;

import com.weiver.applicant.dto.request.put.ApplicantInfoRequestDTO;
import com.weiver.global.common.ApiResponse;
import com.weiver.portfolio.dto.request.PortfolioRequestDTO;
import com.weiver.portfolio.service.PortfolioService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.security.Principal;

@RestController
@RequestMapping("/portfolios")
@RequiredArgsConstructor
public class PortfolioController {
    private final PortfolioService portfolioService;

    public ResponseEntity<ApiResponse<Void>> savePortfolio(
            @RequestPart(value = "requestDTO") @Valid PortfolioRequestDTO requestDTO,
            @RequestPart(value = "portfolio", required = false) MultipartFile portfolio,
            Principal principal){
        
    }

}

package com.weiver.portfolio.service;

import com.weiver.portfolio.dto.request.PortfolioRequestDTO;
import com.weiver.portfolio.dto.request.PortfolioUpdateRequestDTO;
import com.weiver.portfolio.dto.response.PortfolioResponseDTO;
import org.springframework.web.multipart.MultipartFile;

public interface PortfolioService {
    void savePortfolio(PortfolioRequestDTO requestDTO, MultipartFile file, long applicantId);
    void updatePortfolio(PortfolioUpdateRequestDTO requestDTO, long applicantId, long portfolioId);
    PortfolioResponseDTO searchPortfolio(long applicantId);
}

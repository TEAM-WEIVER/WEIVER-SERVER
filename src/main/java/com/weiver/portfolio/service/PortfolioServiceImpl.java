package com.weiver.portfolio.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.portfolio.domain.Portfolio;
import com.weiver.portfolio.dto.request.PortfolioRequestDTO;
import com.weiver.portfolio.dto.request.PortfolioUpdateRequestDTO;
import com.weiver.portfolio.dto.response.PortfolioResponseDTO;
import com.weiver.portfolio.repository.PortfolioRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
@RequiredArgsConstructor
public class PortfolioServiceImpl implements PortfolioService {

    private PortfolioRepository portfolioRepository;
    private ApplicantRepository applicantRepository;
    private S3Service s3Service;

    @Override
    public void savePortfolio(PortfolioRequestDTO requestDTO, MultipartFile file, long applicantId) {
        String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        Long fileSize = file.getSize();
        String fileType = org.springframework.util.StringUtils.getFilenameExtension(fileName);

        String fileKey = s3Service.privateUpload(file, "portfolios");

        Applicant applicant = getApplicant(applicantId);
        Portfolio portfolio = requestDTO.toEntity(applicant, fileSize, fileName, fileType, fileKey);

        portfolioRepository.save(portfolio);
    }



    @Override
    public void updatePortfolio(PortfolioUpdateRequestDTO requestDTO, MultipartFile file,
                                long applicantId, long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        String fileKey = portfolio.getFileKey();

        if(!portfolio.getApplicant().getApplicantId().equals(applicantId)){
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }

        if(file != null && !file.isEmpty()){

            if(StringUtils.hasText(fileKey)){
                s3Service.deleteFile(fileKey);
            }

            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            Long fileSize = file.getSize();
            String fileType = org.springframework.util.StringUtils.getFilenameExtension(fileName);
            fileKey = s3Service.privateUpload(file, "portfolios");

            portfolio.updateFile(fileKey, fileName, fileType, fileSize);
        }

        portfolio.updateLinks(requestDTO);
    }

    @Override
    @Transactional(readOnly = true)
    public PortfolioResponseDTO searchPortfolio(long applicantId) {
        return null;
    }

    private Applicant getApplicant(long applicantId) {
        Applicant applicant = applicantRepository.findById(applicantId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}

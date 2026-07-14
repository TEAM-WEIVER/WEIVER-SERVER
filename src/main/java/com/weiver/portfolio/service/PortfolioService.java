package com.weiver.portfolio.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.event.ApplicantProfileEventService;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.matching.dto.response.PortfolioDetailDTO;
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
public class PortfolioService {

    private final PortfolioRepository portfolioRepository;
    private final ApplicantRepository applicantRepository;
    private final S3Service s3Service;
    private final ApplicantProfileEventService applicantProfileEventService;

    public void savePortfolio(PortfolioRequestDTO requestDTO, MultipartFile file, String publicId) {
        Applicant applicant = getApplicant(publicId);

        String fileName = null;
        Long fileSize = null;
        String fileType = null;
        String fileKey = null;
        if (file != null && !file.isEmpty()) {
            fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            fileSize = file.getSize();
            fileType = StringUtils.getFilenameExtension(fileName);
            fileKey = s3Service.privateUpload(file, "portfolios");
        }

        Portfolio portfolio = requestDTO.toEntity(applicant, fileSize, fileName, fileType, fileKey);

        portfolioRepository.save(portfolio);
        applicantProfileEventService.publishProfileChanged(applicant.getApplicantId());
    }


    public void updatePortfolio(PortfolioUpdateRequestDTO requestDTO, MultipartFile file,
                                String publicId, long portfolioId) {
        Portfolio portfolio = portfolioRepository.findById(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        String fileKey = portfolio.getFileKey();

        if(!portfolio.getApplicant().getPublicId().equals(publicId)){
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
        applicantProfileEventService.publishProfileChanged(portfolio.getApplicant().getApplicantId());
    }

    @Transactional(readOnly = true)
    public PortfolioResponseDTO searchPortfolio(String publicId) {
        Applicant applicant = getApplicant(publicId);

        Portfolio portfolio = portfolioRepository.findByApplicant(applicant)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        String presignedUrl = s3Service.getPresignedUrl(portfolio.getFileKey());
        PortfolioResponseDTO responseDTO = PortfolioResponseDTO.from(portfolio, presignedUrl);

        return responseDTO;
    }

    /**
     * 지원자 포트폴리오 주소 조회
     * */
    public PortfolioDetailDTO getApplicantPortfolio(String publicId) {
        Applicant applicant = getApplicant(publicId);

        Portfolio portfolio = portfolioRepository.findByApplicant(applicant)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        String fileKey = portfolio.getFileKey();
        String presignedUrl = StringUtils.hasText(fileKey) ? s3Service.getPresignedUrl(fileKey) : null;

        return PortfolioDetailDTO.of(portfolio, presignedUrl);
    }

    private Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}

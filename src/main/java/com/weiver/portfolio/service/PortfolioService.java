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
import org.springframework.transaction.annotation.Propagation;
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

    // 진입 메서드는 트랜잭션을 열지 않는다(NOT_SUPPORTED). S3 업로드/삭제는 트랜잭션 경계 밖에서 수행하고,
    // 각 DB 접근은 리포지토리 단위의 짧은 트랜잭션으로 처리한다.
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void savePortfolio(PortfolioRequestDTO requestDTO, MultipartFile file, String publicId) {
        Applicant applicant = getApplicant(publicId);

        // 저장 전 중복 검사 (S3 업로드 이전에 수행해 불필요한 업로드를 막는다)
        if (portfolioRepository.existsByApplicant(applicant)) {
            throw new BusinessException(ErrorCode.PORTFOLIO_ALREADY_EXISTS);
        }

        // S3 업로드는 트랜잭션 경계 밖에서 수행한다.
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


    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void updatePortfolio(PortfolioUpdateRequestDTO requestDTO, MultipartFile file,
                                String publicId, long portfolioId) {
        Portfolio portfolio = portfolioRepository.findWithApplicantByPortfolioId(portfolioId)
                .orElseThrow(() -> new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        if(!portfolio.getApplicant().getPublicId().equals(publicId)){
            throw new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND);
        }

        String previousFileKey = portfolio.getFileKey();
        boolean fileReplaced = file != null && !file.isEmpty();

        if (fileReplaced) {
            String fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            Long fileSize = file.getSize();
            String fileType = StringUtils.getFilenameExtension(fileName);

            // 새 파일 업로드는 트랜잭션 경계 밖에서 수행한다.
            String newFileKey = s3Service.privateUpload(file, "portfolios");

            portfolio.updateFile(newFileKey, fileName, fileType, fileSize);
        }

        portfolio.updateLinks(requestDTO);
        portfolioRepository.save(portfolio);

        // DB 반영이 확정된 뒤 기존 파일을 삭제해 롤백 시 원본 파일이 유실되지 않게 한다.
        if (fileReplaced && StringUtils.hasText(previousFileKey)) {
            s3Service.deleteFile(previousFileKey);
        }

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

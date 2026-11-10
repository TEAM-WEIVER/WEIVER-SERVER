package com.weiver.portfolio.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ProfileSyncStatus;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class PortfolioService {

    private static final String PDF_EXTENSION = "pdf";
    private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);
    // 전역 multipart 한도(spring.servlet.multipart.max-file-size: 10MB)와 같은 기준이다.
    private static final long MAX_PORTFOLIO_FILE_SIZE = 10L * 1024 * 1024;

    private final PortfolioRepository portfolioRepository;
    private final ApplicantRepository applicantRepository;
    private final S3Service s3Service;

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
            validatePortfolioFile(file);
            fileName = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
            fileSize = file.getSize();
            fileType = StringUtils.getFilenameExtension(fileName);
            fileKey = s3Service.privateUpload(file, "portfolios");
        }

        Portfolio portfolio = requestDTO.toEntity(applicant, fileSize, fileName, fileType, fileKey);

        portfolioRepository.save(portfolio);
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
            // 분석 요청 중(REQUESTED)에는 AI 서버가 기존 파일을 내려받기 전에 삭제될 수 있으므로 교체를 막는다.
            // 링크만 수정하는 요청은 파일을 건드리지 않으므로 허용한다.
            if (portfolio.getApplicant().getProfileSyncStatus() == ProfileSyncStatus.REQUESTED) {
                throw new BusinessException(ErrorCode.PORTFOLIO_FILE_LOCKED);
            }
            validatePortfolioFile(file);

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

    /**
     * 포트폴리오 파일은 PDF만, 10MB까지 받는다. S3 업로드 전에 호출한다.
     * Content-Type은 클라이언트가 바꿀 수 있어 기준으로 쓰지 않고, 확장자와 파일 앞 5바이트(%PDF-)로 판별한다.
     */
    private void validatePortfolioFile(MultipartFile file) {
        String extension = StringUtils.getFilenameExtension(file.getOriginalFilename());
        if (!PDF_EXTENSION.equalsIgnoreCase(extension)) {
            throw new BusinessException(ErrorCode.PORTFOLIO_INVALID_FILE_TYPE);
        }
        if (file.getSize() > MAX_PORTFOLIO_FILE_SIZE) {
            throw new BusinessException(ErrorCode.PORTFOLIO_FILE_TOO_LARGE);
        }
        if (!hasPdfSignature(file)) {
            throw new BusinessException(ErrorCode.PORTFOLIO_INVALID_FILE_TYPE);
        }
    }

    private boolean hasPdfSignature(MultipartFile file) {
        try (InputStream inputStream = file.getInputStream()) {
            byte[] header = inputStream.readNBytes(PDF_SIGNATURE.length);
            return Arrays.equals(header, PDF_SIGNATURE);
        } catch (IOException e) {
            // 업로드된 파일을 서버가 읽지 못한 것은 클라이언트 입력 문제가 아니다.
            // S3Service.uploadToS3의 IOException 처리와 같이 log.error(Sentry 전송)로 남기고 INTERNAL_SERVER_ERROR로 응답한다.
            log.error("포트폴리오 파일 헤더 읽기 실패. errorType={}", e.getClass().getSimpleName(), e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR, "파일을 읽을 수 없습니다.");
        }
    }

    private Applicant getApplicant(String publicId) {
        Applicant applicant = applicantRepository.findByPublicId(publicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));
        return applicant;
    }
}

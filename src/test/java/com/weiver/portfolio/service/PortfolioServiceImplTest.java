package com.weiver.portfolio.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.event.ApplicantProfileEventService;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.portfolio.domain.Portfolio;
import com.weiver.portfolio.dto.request.PortfolioRequestDTO;
import com.weiver.portfolio.dto.request.PortfolioUpdateRequestDTO;
import com.weiver.portfolio.dto.response.PortfolioResponseDTO;
import com.weiver.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.multipart.MultipartFile;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class PortfolioServiceImplTest {

    @Mock
    private PortfolioRepository portfolioRepository;

    @Mock
    private ApplicantRepository applicantRepository;

    @Mock
    private S3Service s3Service;

    @Mock
    private ApplicantProfileEventService applicantProfileEventService;

    @InjectMocks
    private PortfolioService portfolioService;

    @Test
    @DisplayName("포트폴리오 최초 저장 후 지원자 프로필 동기화 이벤트를 발행한다")
    void savePortfolio_PublishesApplicantProfileChanged() {
        String publicId = "3333";
        Applicant applicant = Applicant.builder().applicantId(1L).publicId(publicId).build();
        MultipartFile file = mock(MultipartFile.class);
        PortfolioRequestDTO request = new PortfolioRequestDTO(
                "https://github.com/weiver",
                null,
                null
        );

        given(file.getOriginalFilename()).willReturn("portfolio.pdf");
        given(file.getSize()).willReturn(100L);
        given(s3Service.privateUpload(file, "portfolios")).willReturn("portfolios/portfolio.pdf");
        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));

        portfolioService.savePortfolio(request, file, publicId);

        verify(portfolioRepository).save(any(Portfolio.class));
        verify(applicantProfileEventService).publishProfileChanged(1L);
    }

    @Test
    @DisplayName("파일 없이 링크만 최초 저장해도 지원자 프로필 동기화 이벤트를 발행한다")
    void savePortfolio_WithoutFile_PublishesApplicantProfileChanged() {
        String publicId = "3333";
        Applicant applicant = Applicant.builder().applicantId(1L).publicId(publicId).build();
        PortfolioRequestDTO request = new PortfolioRequestDTO(
                "https://github.com/weiver",
                null,
                null
        );

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));

        portfolioService.savePortfolio(request, null, publicId);

        ArgumentCaptor<Portfolio> portfolioCaptor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(portfolioCaptor.capture());
        assertThat(portfolioCaptor.getValue().getFileKey()).isNull();
        assertThat(portfolioCaptor.getValue().getFileName()).isNull();
        verifyNoInteractions(s3Service);
        verify(applicantProfileEventService).publishProfileChanged(1L);
    }

    @Test
    @DisplayName("포트폴리오 수정 후 지원자 프로필 동기화 이벤트를 발행한다")
    void updatePortfolio_PublishesApplicantProfileChanged() {
        String publicId = "3333";
        Applicant applicant = Applicant.builder().applicantId(1L).publicId(publicId).build();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(10L)
                .applicant(applicant)
                .build();
        PortfolioUpdateRequestDTO request = new PortfolioUpdateRequestDTO(
                "https://github.com/weiver-updated",
                null,
                null
        );

        given(portfolioRepository.findById(10L)).willReturn(Optional.of(portfolio));

        portfolioService.updatePortfolio(request, null, publicId, 10L);

        verify(applicantProfileEventService).publishProfileChanged(1L);
    }

    @Test
    @DisplayName("포트폴리오 조회 시 S3 Presigned URL을 발급받아 정상적으로 DTO를 반환한다.")
    void searchPortfolio_Success() {
        // Given
        long applicantId = 1L;
        String publicId = "3333";

        String originalFileKey = "https://weiver-private-bucket/portfolios/uuid-1234.pdf";
        String mockPresignedUrl = "https://weiver-private-bucket/portfolios/uuid-1234.pdf?X-Amz-Algorithm=AWS4-HMAC-SHA256&X-Amz-Credential=...";

        Applicant applicant = Applicant.builder()
                .applicantId(applicantId)
                .publicId(publicId)
                .build();

        Portfolio portfolio = Portfolio.builder()
                .portfolioId(1L)
                .fileKey(originalFileKey)
                .applicant(applicant)
                .build();

        given(applicantRepository.findByPublicId(publicId))
                .willReturn(Optional.of(applicant));
        given(portfolioRepository.findByApplicant(applicant))
                .willReturn(Optional.of(portfolio));

        // S3 서비스가 임시 URL을 발급해 주는 상황을 모방 (AWS 통신 차단)
        given(s3Service.getPresignedUrl(originalFileKey))
                .willReturn(mockPresignedUrl);

        // When
        PortfolioResponseDTO responseDTO = portfolioService.searchPortfolio(publicId);

        // Then
        verify(s3Service, times(1)).getPresignedUrl(originalFileKey);

        assertThat(responseDTO).isNotNull();
         assertThat(responseDTO.downloadUrl()).isEqualTo(mockPresignedUrl);
    }

    @Test
    @DisplayName("엣지 케이스: 회원은 존재하지만 등록된 포트폴리오가 없을 경우 예외 발생")
    void searchPortfolio_NotFound_ThrowsException() {
        // Given
        long applicantId = 1L;
        String publicId = "3333";
        Applicant applicant = Applicant.builder()
                .applicantId(applicantId)
                .publicId(publicId)
                .build();

        given(applicantRepository.findByPublicId(publicId))
                .willReturn(Optional.of(applicant));

        given(portfolioRepository.findByApplicant(applicant))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> portfolioService.searchPortfolio(publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PORTFOLIO_NOT_FOUND);
    }

    @Test
    @DisplayName("엣지 케이스: 존재하지 않는 구직자 ID로 포트폴리오 조회 시 예외 발생")
    void searchPortfolio_ApplicantNotFound_ThrowsException() {
        // Given
        long invalidApplicantId = 999L;
        String invalidPublicId = "3333";

        given(applicantRepository.findByPublicId(invalidPublicId))
                .willReturn(Optional.empty());

        // When & Then
        assertThatThrownBy(() -> portfolioService.searchPortfolio(invalidPublicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);
    }
}

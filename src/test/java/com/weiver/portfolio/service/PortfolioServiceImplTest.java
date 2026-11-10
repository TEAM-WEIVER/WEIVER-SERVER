package com.weiver.portfolio.service;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ProfileSyncStatus;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    @InjectMocks
    private PortfolioService portfolioService;

    private static final String PUBLIC_ID = "3333";
    private static final byte[] PDF_BYTES = "%PDF-1.7\n본문".getBytes(StandardCharsets.UTF_8);
    private static final long MAX_SIZE = 10L * 1024 * 1024;
    private static final String OLD_FILE_URL = "https://weiver-private-bucket/portfolios/old.pdf";

    @Test
    @DisplayName("포트폴리오 최초 저장은 프로필 동기화를 자동으로 트리거하지 않는다(구직자가 프로필 제출 시 수행)")
    void savePortfolio_DoesNotTriggerProfileSync() {
        String publicId = "3333";
        Applicant applicant = Applicant.builder().applicantId(1L).publicId(publicId).build();
        MultipartFile file = pdfFile("portfolio.pdf", PDF_BYTES);
        PortfolioRequestDTO request = new PortfolioRequestDTO(
                "https://github.com/weiver",
                null,
                null
        );

        given(s3Service.privateUpload(file, "portfolios")).willReturn("portfolios/portfolio.pdf");
        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));

        portfolioService.savePortfolio(request, file, publicId);

        verify(portfolioRepository).save(any(Portfolio.class));
    }

    @Test
    @DisplayName("파일 없이 링크만 최초 저장하면 S3 업로드 없이 저장된다")
    void savePortfolio_WithoutFile_SavesWithoutS3Upload() {
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
    }

    @Test
    @DisplayName("이미 포트폴리오가 존재하면 저장 시 PORTFOLIO_ALREADY_EXISTS 예외가 발생한다")
    void savePortfolio_AlreadyExists_ThrowsException() {
        String publicId = "3333";
        Applicant applicant = Applicant.builder().applicantId(1L).publicId(publicId).build();
        PortfolioRequestDTO request = new PortfolioRequestDTO(
                "https://github.com/weiver",
                null,
                null
        );

        given(applicantRepository.findByPublicId(publicId)).willReturn(Optional.of(applicant));
        given(portfolioRepository.existsByApplicant(applicant)).willReturn(true);

        assertThatThrownBy(() -> portfolioService.savePortfolio(request, null, publicId))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PORTFOLIO_ALREADY_EXISTS);

        verifyNoInteractions(s3Service);
        verify(portfolioRepository, times(0)).save(any(Portfolio.class));
    }

    @Test
    @DisplayName("포트폴리오 수정도 프로필 동기화를 자동으로 트리거하지 않는다")
    void updatePortfolio_DoesNotTriggerProfileSync() {
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

        given(portfolioRepository.findWithApplicantByPortfolioId(10L)).willReturn(Optional.of(portfolio));

        portfolioService.updatePortfolio(request, null, publicId, 10L);

        verify(portfolioRepository).save(portfolio);
        verifyNoInteractions(s3Service);
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

    // ===================== R3: PDF·10MB 업로드 제한 =====================

    @Test
    @DisplayName("등록: 확장자가 pdf가 아니면 S3 업로드 없이 PORTFOLIO_INVALID_FILE_TYPE 예외가 발생한다")
    void savePortfolio_NonPdfExtension_Rejected() {
        // given
        givenApplicant();
        MultipartFile file = new MockMultipartFile("portfolio", "portfolio.docx", "application/pdf", PDF_BYTES);

        // when & then
        assertErrorCode(() -> portfolioService.savePortfolio(linkRequest(), file, PUBLIC_ID),
                ErrorCode.PORTFOLIO_INVALID_FILE_TYPE);
        verifyNoInteractions(s3Service);
        verify(portfolioRepository, never()).save(any(Portfolio.class));
    }

    @Test
    @DisplayName("등록: 확장자는 pdf여도 파일 앞 5바이트가 %PDF-가 아니면 PORTFOLIO_INVALID_FILE_TYPE 예외가 발생한다")
    void savePortfolio_PdfExtensionWithoutPdfHeader_Rejected() {
        // given
        givenApplicant();
        MultipartFile zipDisguised = new MockMultipartFile("portfolio", "portfolio.pdf", "application/pdf",
                "PK\u0003\u0004zip".getBytes(StandardCharsets.ISO_8859_1));
        MultipartFile tooShort = new MockMultipartFile("portfolio", "portfolio.pdf", "application/pdf",
                "%PDF".getBytes(StandardCharsets.US_ASCII));

        // when & then
        assertErrorCode(() -> portfolioService.savePortfolio(linkRequest(), zipDisguised, PUBLIC_ID),
                ErrorCode.PORTFOLIO_INVALID_FILE_TYPE);
        assertErrorCode(() -> portfolioService.savePortfolio(linkRequest(), tooShort, PUBLIC_ID),
                ErrorCode.PORTFOLIO_INVALID_FILE_TYPE);
        verifyNoInteractions(s3Service);
        verify(portfolioRepository, never()).save(any(Portfolio.class));
    }

    @Test
    @DisplayName("등록: 10MB를 넘으면 S3 업로드 없이 PORTFOLIO_FILE_TOO_LARGE 예외가 발생한다")
    void savePortfolio_TooLarge_Rejected() {
        // given
        givenApplicant();
        MultipartFile file = pdfFile("portfolio.pdf", pdfOfSize(MAX_SIZE + 1));

        // when & then
        assertErrorCode(() -> portfolioService.savePortfolio(linkRequest(), file, PUBLIC_ID),
                ErrorCode.PORTFOLIO_FILE_TOO_LARGE);
        verifyNoInteractions(s3Service);
        verify(portfolioRepository, never()).save(any(Portfolio.class));
    }

    @Test
    @DisplayName("등록: 정확히 10MB이고 대문자 .PDF 확장자인 PDF는 업로드된다(Content-Type은 판별에 쓰지 않는다)")
    void savePortfolio_ExactlyMaxSizeUppercasePdf_Uploaded() {
        // given
        givenApplicant();
        MultipartFile file = new MockMultipartFile("portfolio", "PORTFOLIO.PDF", "application/octet-stream",
                pdfOfSize(MAX_SIZE));
        given(s3Service.privateUpload(file, "portfolios")).willReturn("https://bucket/portfolios/new.pdf");

        // when
        portfolioService.savePortfolio(linkRequest(), file, PUBLIC_ID);

        // then
        ArgumentCaptor<Portfolio> captor = ArgumentCaptor.forClass(Portfolio.class);
        verify(portfolioRepository).save(captor.capture());
        assertThat(captor.getValue().getFileSize()).isEqualTo(MAX_SIZE);
        assertThat(captor.getValue().getFileKey()).isEqualTo("https://bucket/portfolios/new.pdf");
    }

    @Test
    @DisplayName("교체: PDF가 아닌 파일은 S3 업로드·삭제 없이 PORTFOLIO_INVALID_FILE_TYPE 예외가 발생한다")
    void updatePortfolio_NonPdf_Rejected() {
        // given
        Portfolio portfolio = givenPortfolio(ProfileSyncStatus.PENDING);
        MultipartFile file = new MockMultipartFile("portfolio", "portfolio.hwp", "application/pdf", PDF_BYTES);

        // when & then
        assertErrorCode(() -> portfolioService.updatePortfolio(updateRequest(), file, PUBLIC_ID, 10L),
                ErrorCode.PORTFOLIO_INVALID_FILE_TYPE);
        verify(s3Service, never()).privateUpload(any(), anyString());
        verify(s3Service, never()).deleteFile(anyString());
        verify(portfolioRepository, never()).save(any(Portfolio.class));
        assertThat(portfolio.getFileKey()).isEqualTo(OLD_FILE_URL);
    }

    @Test
    @DisplayName("교체: 10MB를 넘으면 S3 업로드·삭제 없이 PORTFOLIO_FILE_TOO_LARGE 예외가 발생한다")
    void updatePortfolio_TooLarge_Rejected() {
        // given
        givenPortfolio(ProfileSyncStatus.PENDING);
        MultipartFile file = pdfFile("portfolio.pdf", pdfOfSize(MAX_SIZE + 1));

        // when & then
        assertErrorCode(() -> portfolioService.updatePortfolio(updateRequest(), file, PUBLIC_ID, 10L),
                ErrorCode.PORTFOLIO_FILE_TOO_LARGE);
        verify(s3Service, never()).privateUpload(any(), anyString());
        verify(s3Service, never()).deleteFile(anyString());
    }

    // ===================== R5: 분석 중(REQUESTED) 파일 교체 차단 =====================

    @Test
    @DisplayName("교체: 지원자 상태가 REQUESTED면 S3 업로드·삭제 없이 PORTFOLIO_FILE_LOCKED 예외가 발생한다")
    void updatePortfolio_RequestedWithFile_Locked() {
        // given
        Portfolio portfolio = givenPortfolio(ProfileSyncStatus.REQUESTED);
        MultipartFile file = pdfFile("new.pdf", PDF_BYTES);

        // when & then
        assertErrorCode(() -> portfolioService.updatePortfolio(updateRequest(), file, PUBLIC_ID, 10L),
                ErrorCode.PORTFOLIO_FILE_LOCKED);
        verify(s3Service, never()).privateUpload(any(), anyString());
        verify(s3Service, never()).deleteFile(anyString());
        verify(portfolioRepository, never()).save(any(Portfolio.class));
        assertThat(portfolio.getFileKey()).isEqualTo(OLD_FILE_URL);
        assertThat(portfolio.getUrlGithub()).isNull();
    }

    @Test
    @DisplayName("교체: 소유자가 아니면 REQUESTED 여부와 관계없이 PORTFOLIO_NOT_FOUND가 먼저 발생한다")
    void updatePortfolio_RequestedButNotOwner_NotFound() {
        // given
        givenPortfolio(ProfileSyncStatus.REQUESTED);
        MultipartFile file = pdfFile("new.pdf", PDF_BYTES);

        // when & then
        assertErrorCode(() -> portfolioService.updatePortfolio(updateRequest(), file, "other-user", 10L),
                ErrorCode.PORTFOLIO_NOT_FOUND);
        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("교체: REQUESTED 상태여도 파일 없이 링크만 수정하면 허용된다")
    void updatePortfolio_RequestedLinksOnly_Allowed() {
        // given
        Portfolio portfolio = givenPortfolio(ProfileSyncStatus.REQUESTED);

        // when
        portfolioService.updatePortfolio(updateRequest(), null, PUBLIC_ID, 10L);

        // then
        verify(portfolioRepository).save(portfolio);
        assertThat(portfolio.getUrlGithub()).isEqualTo("https://github.com/weiver-updated");
        assertThat(portfolio.getFileKey()).isEqualTo(OLD_FILE_URL);
        verifyNoInteractions(s3Service);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = ProfileSyncStatus.class, names = {"PENDING", "FAILED", "COMPLETED"})
    @DisplayName("교체: REQUESTED가 아닌 상태에서는 새 PDF를 업로드하고 DB 반영 뒤 기존 파일을 삭제한다")
    void updatePortfolio_NotRequested_ReplacesFile(ProfileSyncStatus status) {
        // given
        Portfolio portfolio = givenPortfolio(status);
        MultipartFile file = pdfFile("new.pdf", PDF_BYTES);
        given(s3Service.privateUpload(file, "portfolios")).willReturn("https://weiver-private-bucket/portfolios/new.pdf");

        // when
        portfolioService.updatePortfolio(updateRequest(), file, PUBLIC_ID, 10L);

        // then
        assertThat(portfolio.getFileKey()).isEqualTo("https://weiver-private-bucket/portfolios/new.pdf");
        assertThat(portfolio.getFileName()).isEqualTo("new.pdf");
        assertThat(portfolio.getFileType()).isEqualTo("pdf");
        verify(portfolioRepository).save(portfolio);
        verify(s3Service).deleteFile(OLD_FILE_URL);
    }

    private void givenApplicant() {
        Applicant applicant = Applicant.builder().applicantId(1L).publicId(PUBLIC_ID).build();
        given(applicantRepository.findByPublicId(PUBLIC_ID)).willReturn(Optional.of(applicant));
    }

    private Portfolio givenPortfolio(ProfileSyncStatus status) {
        Applicant applicant = Applicant.builder()
                .applicantId(1L)
                .publicId(PUBLIC_ID)
                .profileSyncStatus(status)
                .build();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(10L)
                .fileKey(OLD_FILE_URL)
                .fileName("old.pdf")
                .fileType("pdf")
                .fileSize(100L)
                .applicant(applicant)
                .build();
        given(portfolioRepository.findWithApplicantByPortfolioId(10L)).willReturn(Optional.of(portfolio));
        return portfolio;
    }

    private PortfolioRequestDTO linkRequest() {
        return new PortfolioRequestDTO("https://github.com/weiver", null, null);
    }

    private PortfolioUpdateRequestDTO updateRequest() {
        return new PortfolioUpdateRequestDTO("https://github.com/weiver-updated", null, null);
    }

    private static MultipartFile pdfFile(String fileName, byte[] content) {
        return new MockMultipartFile("portfolio", fileName, "application/pdf", content);
    }

    private static byte[] pdfOfSize(long size) {
        byte[] content = new byte[(int) size];
        Arrays.fill(content, (byte) ' ');
        System.arraycopy(PDF_BYTES, 0, content, 0, 5);
        return content;
    }

    private static void assertErrorCode(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, ErrorCode expected) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(expected);
    }
}

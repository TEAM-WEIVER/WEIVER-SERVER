package com.weiver.applicant.event;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Certificate;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.event.dto.ApplicantProfileChangedData;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.repository.CertificateRepository;
import com.weiver.applicant.repository.EducationRepository;
import com.weiver.applicant.repository.WorkExperienceRepository;
import com.weiver.applicant.type.Degree;
import com.weiver.applicant.type.EmploymentType;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.weiver.applicant.type.Status;
import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.domain.EssayQuestion;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.s3.dto.S3ObjectLocation;
import com.weiver.global.s3.service.S3Service;
import com.weiver.portfolio.domain.Portfolio;
import com.weiver.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ApplicantProfileEventServiceTest {

    @InjectMocks
    private ApplicantProfileEventService applicantProfileEventService;

    @Mock private ApplicantRepository applicantRepository;
    @Mock private EducationRepository educationRepository;
    @Mock private WorkExperienceRepository workExperienceRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private EssayAnswerRepository essayAnswerRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private S3Service s3Service;
    @Mock private DomainEventPublisher domainEventPublisher;

    private static final String PDF_URL =
            "https://private-bucket.s3.ap-northeast-2.amazonaws.com/portfolios/3f2a-e1.pdf";

    @Test
    @DisplayName("지원자 프로필 변경 이벤트 payload를 구성하고 동기화 상태를 REQUESTED로 바꾼다")
    void publishProfileChanged_BuildsPayloadAndMarksRequested() {
        Applicant applicant = Applicant.builder()
                .applicantId(1L)
                .name("홍길동")
                .email("hong@example.com")
                .phoneNumber("010-1234-5678")
                .birthday(LocalDate.of(2000, 1, 1))
                .build();
        Education education = Education.builder()
                .educationId(10L)
                .degree(Degree.BACHELOR)
                .schoolName("한양대학교")
                .major("컴퓨터공학")
                .gpa(BigDecimal.valueOf(4.0))
                .startDate(YearMonth.of(2020, 3))
                .endDate(YearMonth.of(2024, 2))
                .status(Status.GRADUATED)
                .applicant(applicant)
                .build();
        WorkExperience experience = WorkExperience.builder()
                .experienceId(20L)
                .companyName("Weiver")
                .startDate(LocalDate.of(2024, 1, 1))
                .employmentType(EmploymentType.FULL_TIME)
                .position("Backend Engineer")
                .duties("API 개발")
                .isRecognized(true)
                .applicant(applicant)
                .build();
        Certificate certificate = Certificate.builder()
                .certificateId(30L)
                .certificateName("정보처리기사")
                .issuer("한국산업인력공단")
                .acquisitionDate(LocalDate.of(2023, 6, 1))
                .applicant(applicant)
                .build();
        List<EssayAnswer> answers = List.of(
                essayAnswer(50L, 40L, 1, "자기소개", applicant),
                essayAnswer(51L, 41L, 2, "직무 역량", applicant),
                essayAnswer(52L, 42L, 3, "입사 후 포부", applicant)
        );

        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(educationRepository.findAllByApplicant(applicant)).willReturn(List.of(education));
        given(workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant)).willReturn(List.of(experience));
        given(certificateRepository.findAllByApplicant(applicant)).willReturn(List.of(certificate));
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(answers);

        applicantProfileEventService.publishProfileChanged(1L);

        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publish(captor.capture());

        EventEnvelope<?> envelope = captor.getValue();
        assertThat(envelope.eventType()).isEqualTo(EventType.APPLICANT_PROFILE_CHANGED);
        assertThat(envelope.data()).isInstanceOf(ApplicantProfileChangedData.class);

        ApplicantProfileChangedData data = (ApplicantProfileChangedData) envelope.data();
        assertThat(data.applicantId()).isEqualTo(1L);
        assertThat(data.educations()).hasSize(1);
        assertThat(data.educations().get(0).schoolName()).isEqualTo("한양대학교");
        assertThat(data.experiences().get(0).companyName()).isEqualTo("Weiver");
        assertThat(data.certifications()).containsExactly("정보처리기사");
        assertThat(data.essay().get(0).question()).isEqualTo("자기소개");
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.REQUESTED);
    }

    @Test
    @DisplayName("제출 가능 여부 검증은 호출부가 하므로 이 서비스는 항상 스냅샷을 발행한다")
    void publishProfileChanged_AlwaysPublishesSnapshot() {
        Applicant applicant = Applicant.builder()
                .applicantId(1L)
                .name("홍길동")
                .email("hong@example.com")
                .phoneNumber("010-1234-5678")
                .birthday(LocalDate.of(2000, 1, 1))
                .build();
        Education education = Education.builder()
                .educationId(10L)
                .schoolName("한양대학교")
                .applicant(applicant)
                .build();

        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(educationRepository.findAllByApplicant(applicant)).willReturn(List.of(education));
        given(workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant)).willReturn(List.of());
        given(certificateRepository.findAllByApplicant(applicant)).willReturn(List.of());
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant))
                .willReturn(List.of(essayAnswer(50L, 40L, 1, "자기소개", applicant)));

        applicantProfileEventService.publishProfileChanged(1L);

        verify(domainEventPublisher).publish(any());
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.REQUESTED);
    }

    private EssayAnswer essayAnswer(
            Long answerId,
            Long questionId,
            Integer sequence,
            String questionText,
            Applicant applicant
    ) {
        EssayQuestion question = EssayQuestion.builder()
                .questionId(questionId)
                .sequence(sequence)
                .maxLength(500)
                .question(questionText)
                .build();

        return EssayAnswer.builder()
                .answerId(answerId)
                .answer("답변 본문")
                .applicant(applicant)
                .essayQuestion(question)
                .build();
    }

    @Test
    @DisplayName("동기화 실패 후 재제출 시에도 스냅샷을 다시 발행하고 REQUESTED로 되돌린다")
    void publishProfileChanged_AfterFailedSync_RepublishesSnapshot() {
        Applicant applicant = Applicant.builder()
                .applicantId(1L)
                .name("홍길동")
                .profileSyncStatus(ProfileSyncStatus.FAILED)
                .build();

        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(educationRepository.findAllByApplicant(applicant)).willReturn(List.of());
        given(workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant)).willReturn(List.of());
        given(certificateRepository.findAllByApplicant(applicant)).willReturn(List.of());
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(List.of());

        applicantProfileEventService.publishProfileChanged(1L);

        verify(domainEventPublisher).publish(any());
        assertThat(applicant.getProfileSyncStatus()).isEqualTo(ProfileSyncStatus.REQUESTED);
    }

    @Test
    @DisplayName("PDF 포트폴리오가 있으면 S3 버킷·키와 파일명·크기를 payload에 담는다")
    void publishProfileChanged_WithPdfPortfolio_IncludesFileLocation() {
        // given
        Applicant applicant = givenApplicantWithoutDocuments();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(12L)
                .fileKey(PDF_URL)
                .fileName("포트폴리오.pdf")
                .fileType("pdf")
                .fileSize(2481934L)
                .applicant(applicant)
                .build();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(portfolio));
        given(s3Service.resolveObjectLocation(PDF_URL))
                .willReturn(Optional.of(new S3ObjectLocation("private-bucket", "portfolios/3f2a-e1.pdf")));

        // when
        ApplicantProfileChangedData data = publishAndCapture();

        // then
        assertThat(data.portfolio()).isEqualTo(new ApplicantProfileChangedData.PortfolioData(
                12L,
                new ApplicantProfileChangedData.PortfolioFileData(
                        "private-bucket", "portfolios/3f2a-e1.pdf", "포트폴리오.pdf", 2481934L)
        ));
    }

    @Test
    @DisplayName("파일 유형이 대문자 PDF여도 PDF로 보고 파일 정보를 담는다")
    void publishProfileChanged_UppercasePdfType_IncludesFile() {
        // given
        Applicant applicant = givenApplicantWithoutDocuments();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(12L)
                .fileKey(PDF_URL)
                .fileName("PORTFOLIO.PDF")
                .fileType("PDF")
                .fileSize(10L)
                .applicant(applicant)
                .build();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(portfolio));
        given(s3Service.resolveObjectLocation(PDF_URL))
                .willReturn(Optional.of(new S3ObjectLocation("private-bucket", "portfolios/3f2a-e1.pdf")));

        // when
        ApplicantProfileChangedData data = publishAndCapture();

        // then
        assertThat(data.portfolio().file()).isNotNull();
        assertThat(data.portfolio().file().fileName()).isEqualTo("PORTFOLIO.PDF");
    }

    @Test
    @DisplayName("포트폴리오를 등록하지 않았으면 portfolio는 null이다")
    void publishProfileChanged_WithoutPortfolio_PortfolioIsNull() {
        // given
        Applicant applicant = givenApplicantWithoutDocuments();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.empty());

        // when
        ApplicantProfileChangedData data = publishAndCapture();

        // then
        assertThat(data.portfolio()).isNull();
        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("포트폴리오에 파일이 없으면 portfolio_id만 담고 file은 null이다")
    void publishProfileChanged_PortfolioWithoutFile_FileIsNull() {
        // given
        Applicant applicant = givenApplicantWithoutDocuments();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(12L)
                .urlGithub("https://github.com/weiver")
                .applicant(applicant)
                .build();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(portfolio));

        // when
        ApplicantProfileChangedData data = publishAndCapture();

        // then
        assertThat(data.portfolio()).isEqualTo(new ApplicantProfileChangedData.PortfolioData(12L, null));
        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("이전에 올라간 PDF가 아닌 파일이면 file은 null이다")
    void publishProfileChanged_NonPdfFile_FileIsNull() {
        // given
        Applicant applicant = givenApplicantWithoutDocuments();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(12L)
                .fileKey("https://private-bucket.s3.ap-northeast-2.amazonaws.com/portfolios/old.docx")
                .fileName("이력서.docx")
                .fileType("docx")
                .fileSize(100L)
                .applicant(applicant)
                .build();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(portfolio));

        // when
        ApplicantProfileChangedData data = publishAndCapture();

        // then
        assertThat(data.portfolio()).isEqualTo(new ApplicantProfileChangedData.PortfolioData(12L, null));
        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("파일 URL을 버킷·키로 해석할 수 없으면 file은 null로 두고 이벤트는 발행한다")
    void publishProfileChanged_UnresolvableUrl_FileIsNull() {
        // given
        Applicant applicant = givenApplicantWithoutDocuments();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(12L)
                .fileKey("not-a-url.pdf")
                .fileName("포트폴리오.pdf")
                .fileType("pdf")
                .fileSize(100L)
                .applicant(applicant)
                .build();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(portfolio));
        given(s3Service.resolveObjectLocation("not-a-url.pdf")).willReturn(Optional.empty());

        // when
        ApplicantProfileChangedData data = publishAndCapture();

        // then
        assertThat(data.portfolio()).isEqualTo(new ApplicantProfileChangedData.PortfolioData(12L, null));
    }

    @Test
    @DisplayName("파일 유형이 비어 있으면 원본 파일명 확장자로 PDF 여부를 판별한다")
    void publishProfileChanged_BlankFileType_FallsBackToFileName() {
        // given
        Applicant applicant = givenApplicantWithoutDocuments();
        Portfolio pdfByName = Portfolio.builder()
                .portfolioId(12L)
                .fileKey(PDF_URL)
                .fileName("포트폴리오.pdf")
                .fileSize(10L)
                .applicant(applicant)
                .build();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(pdfByName));
        given(s3Service.resolveObjectLocation(PDF_URL))
                .willReturn(Optional.of(new S3ObjectLocation("private-bucket", "portfolios/3f2a-e1.pdf")));

        // when
        ApplicantProfileChangedData data = publishAndCapture();

        // then
        assertThat(data.portfolio().file()).isNotNull();
    }

    private Applicant givenApplicantWithoutDocuments() {
        Applicant applicant = Applicant.builder()
                .applicantId(1L)
                .name("홍길동")
                .build();
        given(applicantRepository.findById(1L)).willReturn(Optional.of(applicant));
        given(educationRepository.findAllByApplicant(applicant)).willReturn(List.of());
        given(workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant)).willReturn(List.of());
        given(certificateRepository.findAllByApplicant(applicant)).willReturn(List.of());
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(List.of());
        return applicant;
    }

    private ApplicantProfileChangedData publishAndCapture() {
        applicantProfileEventService.publishProfileChanged(1L);
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher).publish(captor.capture());
        return (ApplicantProfileChangedData) captor.getValue().data();
    }
}

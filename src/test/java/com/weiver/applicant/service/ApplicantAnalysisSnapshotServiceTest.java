package com.weiver.applicant.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.weiver.analysis.event.dto.ApplicantAnalysisRequestedData;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import com.weiver.applicant.domain.Certificate;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.repository.AwardRepository;
import com.weiver.applicant.repository.CertificateRepository;
import com.weiver.applicant.repository.EducationRepository;
import com.weiver.applicant.repository.WorkExperienceRepository;
import com.weiver.applicant.type.Degree;
import com.weiver.applicant.type.EmploymentType;
import com.weiver.applicant.type.Status;
import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.domain.EssayQuestion;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.global.s3.dto.S3ObjectLocation;
import com.weiver.global.s3.service.S3Service;
import com.weiver.portfolio.domain.Portfolio;
import com.weiver.portfolio.repository.PortfolioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class ApplicantAnalysisSnapshotServiceTest {

    private static final String PDF_URL =
            "https://private-bucket.s3.ap-northeast-2.amazonaws.com/portfolios/3f2a-e1.pdf";
    private static final S3ObjectLocation PDF_LOCATION =
            new S3ObjectLocation("private-bucket", "portfolios/3f2a-e1.pdf");

    /** Spring Boot 기본 ObjectMapper와 같게 날짜를 문자열로 쓴다. */
    private final ObjectMapper objectMapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    @Mock private EducationRepository educationRepository;
    @Mock private WorkExperienceRepository workExperienceRepository;
    @Mock private CertificateRepository certificateRepository;
    @Mock private AwardRepository awardRepository;
    @Mock private EssayAnswerRepository essayAnswerRepository;
    @Mock private PortfolioRepository portfolioRepository;
    @Mock private S3Service s3Service;

    private ApplicantAnalysisSnapshotService snapshotService;
    private Applicant applicant;

    @BeforeEach
    void setUp() {
        snapshotService = new ApplicantAnalysisSnapshotService(
                educationRepository, workExperienceRepository, certificateRepository, awardRepository,
                essayAnswerRepository, portfolioRepository, s3Service);
        applicant = Applicant.builder().applicantId(1L).name("홍길동").build();
    }

    @Test
    @DisplayName("분석 요청 data를 계약 필드명으로 조립하고, 보내지 않기로 한 필드는 JSON에 없다")
    void assemble_BuildsContractPayload() {
        // given
        givenDocuments(
                List.of(education(10L, "한양대학교")),
                List.of(experience(20L, "Weiver", null, null)),
                List.of(certificate(30L, "정보처리기사")),
                List.of(award(40L, "해커톤 대상", null)),
                List.of(essay(50L, 1, "지원 동기", "답변"))
        );
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(pdfPortfolio()));
        given(s3Service.resolveObjectLocation(PDF_URL)).willReturn(Optional.of(PDF_LOCATION));

        // when
        JsonNode json = objectMapper.valueToTree(snapshotService.assemble(applicant, 3));

        // then
        assertThat(json.get("applicant_id").asLong()).isEqualTo(1L);
        assertThat(json.get("submission_version").asInt()).isEqualTo(3);
        assertThat(json.get("applicant_name").asText()).isEqualTo("홍길동");

        JsonNode education = json.get("educations").get(0);
        assertThat(fieldNames(education))
                .containsExactlyInAnyOrder("education_level", "school_name", "major", "graduation_status");
        assertThat(education.get("education_level").asText()).isEqualTo("BACHELOR");
        assertThat(education.get("graduation_status").asText()).isEqualTo("GRADUATED");

        JsonNode experience = json.get("experiences").get(0);
        assertThat(fieldNames(experience)).containsExactlyInAnyOrder(
                "experience_name", "position_title", "employment_type", "start_date", "end_date", "responsibilities");
        assertThat(experience.get("start_date").asText()).isEqualTo("2025-01-01");
        assertThat(experience.get("end_date").isNull()).isTrue();
        assertThat(experience.get("responsibilities").isNull()).isTrue();

        assertThat(json.get("certifications").get(0).asText()).isEqualTo("정보처리기사");
        assertThat(json.get("certifications").get(0).isTextual()).isTrue();

        JsonNode award = json.get("awards").get(0);
        assertThat(fieldNames(award)).containsExactlyInAnyOrder("id", "award_name", "issuer", "award_date");
        assertThat(award.get("id").asLong()).isEqualTo(40L);
        assertThat(award.get("issuer").isNull()).isTrue();
        assertThat(award.get("award_date").asText()).isEqualTo("2025-05-01");

        JsonNode essay = json.get("essay").get(0);
        assertThat(fieldNames(essay)).containsExactlyInAnyOrder("id", "question", "answer");

        JsonNode portfolio = json.get("portfolio");
        assertThat(fieldNames(portfolio)).containsExactlyInAnyOrder("portfolio_id", "file");
        JsonNode file = portfolio.get("file");
        assertThat(fieldNames(file))
                .containsExactlyInAnyOrder("bucket", "key", "file_name", "file_size", "version_id");
        assertThat(file.get("bucket").asText()).isEqualTo("private-bucket");
        assertThat(file.get("key").asText()).isEqualTo("portfolios/3f2a-e1.pdf");
        assertThat(file.get("file_name").asText()).isEqualTo("포트폴리오.pdf");
        assertThat(file.get("file_size").asLong()).isEqualTo(2481934L);
        assertThat(file.get("version_id").isNull()).isTrue();

        assertThat(fieldNames(json)).containsExactlyInAnyOrder(
                "applicant_id", "submission_version", "applicant_name", "educations", "experiences",
                "certifications", "essay", "awards", "portfolio");
    }

    @Test
    @DisplayName("수상 이력이 없으면 awards는 빈 배열이다")
    void assemble_NoAwards_EmptyArray() {
        // given
        givenNoDocuments();

        // when
        JsonNode json = objectMapper.valueToTree(snapshotService.assemble(applicant, 1));

        // then
        assertThat(json.get("awards").isArray()).isTrue();
        assertThat(json.get("awards")).isEmpty();
    }

    @Test
    @DisplayName("포트폴리오를 등록하지 않았으면 portfolio는 null이고 필드는 남는다")
    void assemble_WithoutPortfolio_PortfolioIsNull() {
        // given
        givenNoDocuments();

        // when
        JsonNode json = objectMapper.valueToTree(snapshotService.assemble(applicant, 1));

        // then
        assertThat(json.has("portfolio")).isTrue();
        assertThat(json.get("portfolio").isNull()).isTrue();
        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("포트폴리오에 파일이 없으면 portfolio_id만 담고 file은 null이다")
    void assemble_PortfolioWithoutFile_FileIsNull() {
        // given
        givenNoDocuments();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(12L).urlGithub("https://github.com/weiver").applicant(applicant).build();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(portfolio));

        // when
        ApplicantAnalysisRequestedData data = snapshotService.assemble(applicant, 1);

        // then
        assertThat(data.portfolio()).isEqualTo(new ApplicantAnalysisRequestedData.PortfolioData(12L, null));
        JsonNode json = objectMapper.valueToTree(data);
        assertThat(json.get("portfolio").has("file")).isTrue();
        assertThat(json.get("portfolio").get("file").isNull()).isTrue();
        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("PDF가 아닌 파일이면 file은 null이다")
    void assemble_NonPdfFile_FileIsNull() {
        // given
        givenNoDocuments();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(12L)
                .fileKey("https://private-bucket.s3.ap-northeast-2.amazonaws.com/portfolios/old.docx")
                .fileName("이력서.docx").fileType("docx").fileSize(100L)
                .applicant(applicant).build();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(portfolio));

        // when
        ApplicantAnalysisRequestedData data = snapshotService.assemble(applicant, 1);

        // then
        assertThat(data.portfolio()).isEqualTo(new ApplicantAnalysisRequestedData.PortfolioData(12L, null));
        verifyNoInteractions(s3Service);
    }

    @Test
    @DisplayName("저장된 URL을 버킷·키로 해석할 수 없으면 file은 null이다")
    void assemble_UnresolvableUrl_FileIsNull() {
        // given
        givenNoDocuments();
        Portfolio portfolio = Portfolio.builder()
                .portfolioId(12L).fileKey("not-a-url.pdf").fileName("포트폴리오.pdf").fileType("pdf")
                .fileSize(100L).applicant(applicant).build();
        given(portfolioRepository.findByApplicant(applicant)).willReturn(Optional.of(portfolio));
        given(s3Service.resolveObjectLocation("not-a-url.pdf")).willReturn(Optional.empty());

        // when
        ApplicantAnalysisRequestedData data = snapshotService.assemble(applicant, 1);

        // then
        assertThat(data.portfolio()).isEqualTo(new ApplicantAnalysisRequestedData.PortfolioData(12L, null));
    }

    @Test
    @DisplayName("파일 유형이 대문자 PDF이거나 비어 있고 파일명이 .pdf면 PDF로 본다")
    void assemble_PdfDetection_CaseInsensitiveAndFallsBackToFileName() {
        // given
        givenNoDocuments();
        Portfolio uppercase = Portfolio.builder()
                .portfolioId(12L).fileKey(PDF_URL).fileName("PORTFOLIO.PDF").fileType("PDF").fileSize(10L)
                .applicant(applicant).build();
        Portfolio blankType = Portfolio.builder()
                .portfolioId(13L).fileKey(PDF_URL).fileName("포트폴리오.pdf").fileSize(10L)
                .applicant(applicant).build();
        given(portfolioRepository.findByApplicant(applicant))
                .willReturn(Optional.of(uppercase), Optional.of(blankType));
        given(s3Service.resolveObjectLocation(PDF_URL)).willReturn(Optional.of(PDF_LOCATION));

        // when
        ApplicantAnalysisRequestedData first = snapshotService.assemble(applicant, 1);
        ApplicantAnalysisRequestedData second = snapshotService.assemble(applicant, 1);

        // then
        assertThat(first.portfolio().file()).isNotNull();
        assertThat(first.portfolio().file().fileName()).isEqualTo("PORTFOLIO.PDF");
        assertThat(first.portfolio().file().versionId()).isNull();
        assertThat(second.portfolio().file()).isNotNull();
    }

    @Test
    @DisplayName("같은 내용이면 항목 조회 순서와 제출 버전이 달라도 content_hash가 같다")
    void contentHash_SameContent_IgnoresOrderAndVersion() {
        // given
        Education eduA = education(10L, "한양대학교");
        Education eduB = education(11L, "서울대학교");
        WorkExperience expA = experience(20L, "Weiver", LocalDate.of(2025, 6, 30), "백엔드");
        WorkExperience expB = experience(21L, "Acme", null, null);
        Certificate certA = certificate(30L, "정보처리기사");
        Certificate certB = certificate(31L, "SQLD");
        Award awardA = award(40L, "해커톤 대상", "한양대");
        Award awardB = award(41L, "공모전 우수상", null);
        EssayAnswer essayA = essay(50L, 1, "지원 동기", "답변1");
        EssayAnswer essayB = essay(51L, 2, "성장 과정", "답변2");

        givenDocuments(List.of(eduA, eduB), List.of(expA, expB), List.of(certA, certB),
                List.of(awardA, awardB), List.of(essayA, essayB));
        String first = hashOf(snapshotService.assemble(applicant, 1));

        givenDocuments(List.of(eduB, eduA), List.of(expB, expA), List.of(certB, certA),
                List.of(awardB, awardA), List.of(essayB, essayA));

        // when
        String second = hashOf(snapshotService.assemble(applicant, 2));

        // then
        assertThat(second).isEqualTo(first);
        assertThat(first).matches("[0-9a-f]{64}");
    }

    @Test
    @DisplayName("내용이 바뀌면 content_hash가 달라진다")
    void contentHash_ChangedContent_Differs() {
        // given
        givenDocuments(List.of(education(10L, "한양대학교")), List.of(), List.of(), List.of(), List.of());
        String before = hashOf(snapshotService.assemble(applicant, 1));
        givenDocuments(List.of(education(10L, "서울대학교")), List.of(), List.of(), List.of(), List.of());

        // when
        String after = hashOf(snapshotService.assemble(applicant, 1));

        // then
        assertThat(after).isNotEqualTo(before);
    }

    @Test
    @DisplayName("content_hash는 맵의 키 순서에 영향을 받지 않는다")
    void contentHash_IgnoresMapKeyOrder() {
        // given
        Map<String, Object> ordered = new java.util.LinkedHashMap<>();
        ordered.put("a", 1);
        ordered.put("b", Map.of("x", 1, "y", 2));
        Map<String, Object> reversed = new java.util.LinkedHashMap<>();
        reversed.put("b", new java.util.TreeMap<>(Map.of("y", 2, "x", 1)).descendingMap());
        reversed.put("a", 1);

        // when & then
        assertThat(snapshotService.contentHash(reversed)).isEqualTo(snapshotService.contentHash(ordered));
    }

    private String hashOf(ApplicantAnalysisRequestedData data) {
        return snapshotService.contentHash(objectMapper.convertValue(data, new TypeReference<Map<String, Object>>() {}));
    }

    private void givenNoDocuments() {
        givenDocuments(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private void givenDocuments(List<Education> educations, List<WorkExperience> experiences,
                                List<Certificate> certificates, List<Award> awards, List<EssayAnswer> essays) {
        given(educationRepository.findAllByApplicant(applicant)).willReturn(educations);
        given(workExperienceRepository.findAllByApplicantOrderByStartDateDesc(applicant)).willReturn(experiences);
        given(certificateRepository.findAllByApplicant(applicant)).willReturn(certificates);
        given(awardRepository.findAllByApplicant(applicant)).willReturn(awards);
        given(essayAnswerRepository.findAllByApplicantWithQuestionOrderBySequence(applicant)).willReturn(essays);
    }

    private Portfolio pdfPortfolio() {
        return Portfolio.builder()
                .portfolioId(12L).fileKey(PDF_URL).fileName("포트폴리오.pdf").fileType("pdf").fileSize(2481934L)
                .applicant(applicant).build();
    }

    private Education education(Long id, String schoolName) {
        return Education.builder()
                .educationId(id).degree(Degree.BACHELOR).schoolName(schoolName).major("컴퓨터공학")
                .gpa(BigDecimal.valueOf(4.0)).startDate(YearMonth.of(2021, 3)).endDate(YearMonth.of(2025, 2))
                .status(Status.GRADUATED).applicant(applicant).build();
    }

    private WorkExperience experience(Long id, String companyName, LocalDate endDate, String duties) {
        return WorkExperience.builder()
                .experienceId(id).companyName(companyName).startDate(LocalDate.of(2025, 1, 1)).endDate(endDate)
                .employmentType(EmploymentType.INTERN).position("인턴").duties(duties).isRecognized(true)
                .applicant(applicant).build();
    }

    private Certificate certificate(Long id, String name) {
        return Certificate.builder()
                .certificateId(id).certificateName(name).issuer("한국산업인력공단")
                .acquisitionDate(LocalDate.of(2024, 6, 1)).applicant(applicant).build();
    }

    private Award award(Long id, String name, String issuer) {
        return Award.builder()
                .awardId(id).awardName(name).issuer(issuer).awardDate(LocalDate.of(2025, 5, 1))
                .applicant(applicant).build();
    }

    private EssayAnswer essay(Long answerId, int sequence, String question, String answer) {
        EssayQuestion essayQuestion = EssayQuestion.builder()
                .questionId((long) sequence).sequence(sequence).maxLength(1000).question(question).build();
        return EssayAnswer.builder()
                .answerId(answerId).answer(answer).applicant(applicant).essayQuestion(essayQuestion).build();
    }

    private List<String> fieldNames(JsonNode node) {
        List<String> names = new java.util.ArrayList<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}

package com.weiver.matching.service;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.DetailAnalysisReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.dto.response.AnalysisReportDto;
import com.weiver.analysis.dto.response.CultureFitSummaryDTO;
import com.weiver.analysis.service.ReportService;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.dto.response.ApplicantProfileDto;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.applicant.service.WorkExperienceService;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.service.InterviewSessionService;
import com.weiver.interview.type.InterviewType;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.dto.response.*;
import com.weiver.portfolio.service.PortfolioService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MatchResultReportServiceTest {

    private static final Long JD_ID = 1L;
    private static final String APPLICANT_PUBLIC_ID = "app-123";
    private static final String COMPANY_PUBLIC_ID = "comp-456";

    @InjectMocks
    private MatchResultReportService matchResultReportService;

    @Mock
    private ApplicantService applicantService;
    @Mock
    private ReportService reportService;
    @Mock
    private MatchResultService matchResultService;
    @Mock
    private PortfolioService portfolioService;
    @Mock
    private WorkExperienceService workExperienceService;
    @Mock
    private InterviewSessionService interviewSessionService;

    @Test
    @DisplayName("[getCardSummary] 정상: 상단 프로필과 우측 요약 카드를 정상적으로 조합하여 반환한다")
    void getCardSummary_ReturnsCorrectCardSummary() {
        // given
        MatchResult matchResult = MatchResult.builder()
                .matchingRate(95.0f)
                .note("훌륭한 지원자입니다.")
                .build();

        ApplicantProfileDto profileDto = new ApplicantProfileDto(Applicant.builder().name("홍길동").build(), "3년차 백엔드 개발자");

        CultureReport cultureReport = CultureReport.builder().culturefitStyles(CulturefitStyle.STEADY_SUPPORTER).build();
        TechnicalSkillReport technicalSkillReport = TechnicalSkillReport.builder().skillTags(List.of("Java", "Spring")).build();
        AnalysisReportDto analysisDto = new AnalysisReportDto(cultureReport, technicalSkillReport);

        givenValidatedMatchResult(matchResult);
        given(applicantService.getApplicantProfile(APPLICANT_PUBLIC_ID)).willReturn(profileDto);
        given(reportService.getApplicantReport(APPLICANT_PUBLIC_ID)).willReturn(analysisDto);

        // when
        ApplicantCardResponseDTO response = matchResultReportService.getCardSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.memo()).isEqualTo("훌륭한 지원자입니다.");
        assertThat(response.cardDetailDTO().skillScore()).isEqualTo(95.0f);
        assertThat(response.profileDetailDTO().name()).isEqualTo("홍길동");
    }

    @Test
    @DisplayName("[getSummaryCard] 정상: AI 요약과 주요 경력 리스트를 정상적으로 반환한다")
    void getSummaryCard_ReturnsSummaryAndCareers() {
        // given
        MatchResult matchResult = MatchResult.builder().aiSummary("경험이 풍부합니다.").build();
        List<MajorCareerDTO> careers = List.of(
                new MajorCareerDTO(
                        2L,
                        "네이버",
                        "백엔드 개발",
                        "정규직",
                        LocalDate.of(2022, 12, 1),
                        LocalDate.of(2024, 12, 1),
                        "주요 업무 및 성과"
                )
        );

        givenValidatedMatchResult(matchResult);
        given(workExperienceService.getCareerSummary(APPLICANT_PUBLIC_ID)).willReturn(careers);

        // when
        SummaryCardResponseDTO response = matchResultReportService.getSummaryCard(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.aiSummary()).isEqualTo("경험이 풍부합니다.");
        assertThat(response.majorCareerDTO()).hasSize(1);
    }

    @Test
    @DisplayName("[getSkillFitSummary] 정상: 스킬 점수를 백분율로 변환하고 우선순위 요약 멘트를 생성한다")
    void getSkillFitSummary_ReturnsSkillAnalysisFromReports() {
        // given
        MatchResult matchResult = MatchResult.builder()
                .jobPosting(JobPosting.builder()
                        .competencyPriorities(List.of("learning", "logic", "collaboration"))
                        .build())
                .matchingRate(87.5f)
                .build();
        DetailAnalysisReport detailReport = DetailAnalysisReport.builder()
                .skillAnalysis(Map.of(
                        "criteria_summary", Map.of(
                                "learning", Map.of("average_score", 4.5),
                                "logic", Map.of("average_score", 5.0),
                                "collaboration", Map.of("average_score", 3.0)
                        )
                ))
                .build();
        TechnicalSkillReport technicalSkillReport = TechnicalSkillReport.builder()
                .skillTags(List.of("Java", "Spring Boot", "JPA"))
                .build();

        givenValidatedMatchResult(matchResult);
        given(reportService.getDetailAnalysisReport(APPLICANT_PUBLIC_ID)).willReturn(detailReport);
        given(reportService.getTechnicalSkillReport(APPLICANT_PUBLIC_ID)).willReturn(technicalSkillReport);

        // when
        SkillFitSummaryDTO response = matchResultReportService.getSkillFitSummary(
                JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.matchingRate()).isEqualTo(87.5f);
        assertThat(response.skillTags()).containsExactly("Java", "Spring Boot", "JPA");
        assertThat(response.aiSkillAnalysis()).extracting("percentage").containsExactlyInAnyOrder(90, 100, 60);
        assertThat(response.aiAbilitySummary()).contains("1순위").contains("90%").contains("100%");
    }

    @Test
    @DisplayName("[getSkillFitSummary] 엣지: AI 분석 결과(skillAnalysisMap)가 null일 경우 분석 불가 멘트와 빈 차트를 반환한다")
    void getSkillFitSummary_ReturnsFallbackWhenAnalysisMapIsNull() {
        // given
        MatchResult matchResult = MatchResult.builder()
                .jobPosting(JobPosting.builder().competencyPriorities(List.of("learning")).build())
                .build();
        DetailAnalysisReport detailReport = DetailAnalysisReport.builder()
                .skillAnalysis(null) // 💡 명시적 null
                .build();
        TechnicalSkillReport technicalSkillReport = TechnicalSkillReport.builder().skillTags(List.of()).build();

        givenValidatedMatchResult(matchResult);
        given(reportService.getDetailAnalysisReport(APPLICANT_PUBLIC_ID)).willReturn(detailReport);
        given(reportService.getTechnicalSkillReport(APPLICANT_PUBLIC_ID)).willReturn(technicalSkillReport);

        // when
        SkillFitSummaryDTO response = matchResultReportService.getSkillFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.aiSkillAnalysis()).isEmpty();
        assertThat(response.aiAbilitySummary()).isEqualTo("우선순위 역량 평가 데이터를 분석할 수 없습니다.");
    }

    @Test
    @DisplayName("[getSkillFitSummary] 엣지: 우선순위 기준과 일치하는 분석 결과가 없을 경우 대체 요약 멘트를 반환한다")
    void getSkillFitSummary_ReturnsFallbackWhenSkillAnalysisIsMissing() {
        // given
        MatchResult matchResult = MatchResult.builder()
                .jobPosting(JobPosting.builder().competencyPriorities(List.of("learning")).build())
                .build();
        DetailAnalysisReport detailReport = DetailAnalysisReport.builder()
                .skillAnalysis(Map.of("criteria_summary", Map.of("unexpected", Map.of("average_score", 5.0))))
                .build();
        TechnicalSkillReport technicalSkillReport = TechnicalSkillReport.builder().skillTags(List.of()).build();

        givenValidatedMatchResult(matchResult);
        given(reportService.getDetailAnalysisReport(APPLICANT_PUBLIC_ID)).willReturn(detailReport);
        given(reportService.getTechnicalSkillReport(APPLICANT_PUBLIC_ID)).willReturn(technicalSkillReport);

        // when
        SkillFitSummaryDTO response = matchResultReportService.getSkillFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.aiAbilitySummary()).isEqualTo("우선순위 역량과 일치하는 분석 결과가 없습니다.");
    }

    @Test
    @DisplayName("[getCultureFitSummary] 정상: 문화 축 점수를 백분율로 변환하고 상위 두 개의 축을 정확히 추출한다")
    void getCultureFitSummary_ReturnsCultureAxesSortedByPercentage() {
        // given
        MatchResult matchResult = MatchResult.builder().matchingRate(80.0f).aiSummary("culture summary").build();
        DetailAnalysisReport detailReport = DetailAnalysisReport.builder()
                .cultureAnalysis(Map.of(
                        "culture_axis", Map.of(
                                "openness_to_change", 0.91,
                                "self_enhancement", 0.42,
                                "conservation", 0.75,
                                "self_transcendence", 0.63
                        ),
                        "extracted_culturefit", Map.of("자기방향", 0.81, "자극", 0.71)
                ))
                .build();
        CultureReport cultureReport = CultureReport.builder().culturefitStyles(CulturefitStyle.STEADY_SUPPORTER).build();

        givenValidatedMatchResult(matchResult);
        given(reportService.getDetailAnalysisReport(APPLICANT_PUBLIC_ID)).willReturn(detailReport);
        given(reportService.getCultureReport(APPLICANT_PUBLIC_ID)).willReturn(cultureReport);

        // when
        CultureFitSummaryDTO response = matchResultReportService.getCultureFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.matchStatus()).isEqualTo("높은 매칭률");
        assertThat(response.topTwoAxes()).extracting("percentage").containsExactly(91, 75); // 상위 2개 확인
    }

    @Test
    @DisplayName("[getCultureFitSummary] 엣지: AI 분석 데이터(cultureAnalysisMap)가 null일 경우 예외 없이 빈 축 리스트를 반환한다")
    void getCultureFitSummary_ReturnsEmptyAxesWhenCultureAnalysisIsNull() {
        // given
        MatchResult matchResult = MatchResult.builder().matchingRate(79.9f).aiSummary("culture summary").build();
        DetailAnalysisReport detailReport = DetailAnalysisReport.builder().cultureAnalysis(null).build(); // 💡 명시적 null
        CultureReport cultureReport = CultureReport.builder().culturefitStyles(CulturefitStyle.AGGRESSIVE_INNOVATOR).build();

        givenValidatedMatchResult(matchResult);
        given(reportService.getDetailAnalysisReport(APPLICANT_PUBLIC_ID)).willReturn(detailReport);
        given(reportService.getCultureReport(APPLICANT_PUBLIC_ID)).willReturn(cultureReport);

        // when
        CultureFitSummaryDTO response = matchResultReportService.getCultureFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.axesDetails()).isEmpty();
        assertThat(response.topTwoAxes()).isEmpty();
        assertThat(response.matchStatus()).isEqualTo("보통 매칭률"); // 80점 미만
    }


    @Test
    @DisplayName("[getDocumentTabSummary] 정상: 포트폴리오와 면접 스크립트를 정상적으로 조회하여 반환한다")
    void getDocumentTabSummary_ReturnsPortfolioAndScripts() {
        // given
        MatchResult matchResult = MatchResult.builder().build();
        PortfolioDetailDTO portfolioDto = new PortfolioDetailDTO("s3://file", "github", null, null);
        List<InterviewScriptDTO> techScripts = List.of(new InterviewScriptDTO("Q", "A"));

        givenValidatedMatchResult(matchResult);
        given(portfolioService.getApplicantPortfolio(APPLICANT_PUBLIC_ID)).willReturn(portfolioDto);
        given(interviewSessionService.getInterviewScripts(APPLICANT_PUBLIC_ID, InterviewType.TECH.name())).willReturn(techScripts);

        // when
        DocumentTabSummaryDTO response = matchResultReportService.getDocumentTabSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.portfolioDetailDTO().portfolioFileUrl()).isEqualTo("s3://file");
        assertThat(response.techInterviewScripts()).hasSize(1);
    }

    @Test
    @DisplayName("[getDocumentTabSummary] 엣지: 포트폴리오가 존재하지 않을 경우 빈 포트폴리오 정보를 담아 정상 반환한다 (Graceful Degradation)")
    void getDocumentTabSummary_ReturnsEmptyPortfolioWhenPortfolioNotFound() {
        // given
        MatchResult matchResult = MatchResult.builder().build();
        givenValidatedMatchResult(matchResult);

        // 💡 포트폴리오 없음 에러 발생 설정
        given(portfolioService.getApplicantPortfolio(APPLICANT_PUBLIC_ID))
                .willThrow(new BusinessException(ErrorCode.PORTFOLIO_NOT_FOUND));

        // when
        DocumentTabSummaryDTO response = matchResultReportService.getDocumentTabSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.portfolioDetailDTO().portfolioFileUrl()).isNull();
        assertThat(response.portfolioDetailDTO().urlGithub()).isNull();
        verify(matchResultService).getValidatedMatchResult(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);
    }

    @Test
    @DisplayName("[getDocumentTabSummary] 엣지: 포트폴리오 조회 중 '포트폴리오 없음' 외의 비즈니스 예외가 발생하면 그대로 예외를 던진다")
    void getDocumentTabSummary_RethrowsUnexpectedBusinessException() {
        // given
        MatchResult matchResult = MatchResult.builder().build();
        BusinessException unexpectedException = new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);

        givenValidatedMatchResult(matchResult);
        given(portfolioService.getApplicantPortfolio(APPLICANT_PUBLIC_ID)).willThrow(unexpectedException);

        // when & then
        assertThatThrownBy(() -> matchResultReportService.getDocumentTabSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .isSameAs(unexpectedException);
    }

    // 💡 공통 헬퍼 메서드
    private void givenValidatedMatchResult(MatchResult matchResult) {
        given(matchResultService.getValidatedMatchResult(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .willReturn(matchResult);
    }
}
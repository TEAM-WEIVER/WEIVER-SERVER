package com.weiver.matching.service;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.DetailAnalysisReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.dto.response.AnalysisReportDTO;
import com.weiver.analysis.dto.response.AxisDetailDTO;
import com.weiver.analysis.dto.response.CultureFitSummaryDTO;
import com.weiver.analysis.dto.response.SubTraitDTO;
import com.weiver.analysis.service.CulturefitAxisService;
import com.weiver.analysis.service.ReportService;
import com.weiver.analysis.type.CultureAxis;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.dto.response.ApplicantProfileDTO;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.applicant.service.WorkExperienceService;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.interview.dto.response.InterviewTurnDTO;
import com.weiver.interview.service.InterviewSessionService;
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
                .skillScore(95.0f)
                .matchingRate(95.0f)
                .note("훌륭한 지원자입니다.")
                .build();

        ApplicantProfileDTO profileDto = new ApplicantProfileDTO(Applicant.builder().name("홍길동").build(), "3년차 백엔드 개발자");

        CultureReport cultureReport = CultureReport.builder().culturefitStyles(CulturefitStyle.STEADY_SUPPORTER).build();
        TechnicalSkillReport technicalSkillReport = TechnicalSkillReport.builder().skillTags(List.of("Java", "Spring")).build();
        AnalysisReportDTO analysisDto = new AnalysisReportDTO(cultureReport, technicalSkillReport);

        givenValidatedMatchResult(matchResult);
        given(applicantService.getApplicantProfile(APPLICANT_PUBLIC_ID)).willReturn(profileDto);
        given(reportService.getApplicantReport(APPLICANT_PUBLIC_ID)).willReturn(analysisDto);

        // when
        ApplicantCardResponseDTO response = matchResultReportService.getCardSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.memo()).isEqualTo("훌륭한 지원자입니다.");
        assertThat(response.cardDetailDTO().skillScore()).isEqualTo(95);
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
    @DisplayName("[getCultureFitSummary] 하위호환: culture_axis에 4축 키가 들어 있던 과거 데이터도 백분율로 변환한다")
    void getCultureFitSummary_FallsBackToLegacyCultureAxisKeys() {
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
    @DisplayName("[getCultureFitSummary] 정상: culture_axis가 x/y 좌표여도 파생 저장된 culture_axis_scores로 4축 백분율을 만든다")
    void getCultureFitSummary_UsesDerivedCultureAxisScores() {
        // given
        MatchResult matchResult = MatchResult.builder().matchingRate(80.0f).aiSummary("culture summary").build();
        DetailAnalysisReport detailReport = DetailAnalysisReport.builder()
                .cultureAnalysis(Map.of(
                        // 새 계약: culture_axis는 2차원 좌표, 4축 점수는 culture_axis_scores에 파생 저장된다.
                        "culture_axis", Map.of("x_axis", 0.1234, "y_axis", -0.0567),
                        "culture_axis_scores", Map.of(
                                "openness_to_change", 0.91,
                                "self_enhancement", 0.42,
                                "conservation", 0.75,
                                "self_transcendence", 0.63
                        ),
                        "extracted_culturefit", Map.of("자기방향", 0.81, "자극", 0.71)
                ))
                .build();
        CultureReport cultureReport = CultureReport.builder()
                .culturefitStyles(CulturefitStyle.INCLUSIVE_INNOVATOR)
                .build();

        givenValidatedMatchResult(matchResult);
        given(reportService.getDetailAnalysisReport(APPLICANT_PUBLIC_ID)).willReturn(detailReport);
        given(reportService.getCultureReport(APPLICANT_PUBLIC_ID)).willReturn(cultureReport);

        // when
        CultureFitSummaryDTO response = matchResultReportService.getCultureFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.axesDetails()).hasSize(4);
        assertThat(response.topTwoAxes()).extracting("percentage").containsExactly(91, 75);
        assertThat(response.culturefitStyle()).isEqualTo(CulturefitStyle.INCLUSIVE_INNOVATOR.getDescription());
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
        List<InterviewTurnDTO> interviewTurns = List.of(
                new InterviewTurnDTO("S_01_00", 1, "Q", "A"),
                new InterviewTurnDTO("C_01_00", 2, "CQ", "CA")
        );

        givenValidatedMatchResult(matchResult);
        given(portfolioService.getApplicantPortfolio(APPLICANT_PUBLIC_ID)).willReturn(portfolioDto);
        given(interviewSessionService.getLatestInterviewTurns(APPLICANT_PUBLIC_ID)).willReturn(interviewTurns);

        // when
        DocumentTabSummaryDTO response = matchResultReportService.getDocumentTabSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then
        assertThat(response.portfolioDetailDTO().portfolioFileUrl()).isEqualTo("s3://file");
        assertThat(response.techInterviewScripts()).hasSize(1);
        assertThat(response.cultureInterviewScripts()).hasSize(1);
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
    @Test
    @DisplayName("[getCultureFitSummary] 회귀: 축 게이지 %와 하위 가치 목록이 CultureAxis 매핑 하나에서 나온다")
    void getCultureFitSummary_AxisGaugeMatchesItsOwnSubTraits() {
        // given - 실제 CulturefitAxisService가 계산한 축 점수를 그대로 저장한 상태를 재현한다.
        // 하드코딩한 culture_axis_scores를 쓰면 계산 쪽과 표시 쪽의 그룹핑 불일치를 잡지 못한다.
        Map<String, Double> extractedCulturefit = Map.of(
                "자기방향", 0.72,
                "자극", 0.55,
                "쾌락", 0.31,
                "성취", 0.84,
                "권력", 0.22,
                "안전", 0.61,
                "순응", 0.48,
                "전통", 0.35,
                "호의", 0.76,
                "보편주의", 0.69
        );
        Map<String, Double> axisScores = new CulturefitAxisService().calculateAxisScores(extractedCulturefit);

        MatchResult matchResult = MatchResult.builder().matchingRate(80.0f).aiSummary("culture summary").build();
        DetailAnalysisReport detailReport = DetailAnalysisReport.builder()
                .cultureAnalysis(Map.of(
                        "culture_axis", Map.of("x_axis", 0.1234, "y_axis", -0.0567),
                        "culture_axis_scores", axisScores,
                        "extracted_culturefit", extractedCulturefit
                ))
                .build();
        CultureReport cultureReport = CultureReport.builder()
                .culturefitStyles(CulturefitStyle.INCLUSIVE_INNOVATOR)
                .build();

        givenValidatedMatchResult(matchResult);
        given(reportService.getDetailAnalysisReport(APPLICANT_PUBLIC_ID)).willReturn(detailReport);
        given(reportService.getCultureReport(APPLICANT_PUBLIC_ID)).willReturn(cultureReport);

        // when
        CultureFitSummaryDTO response = matchResultReportService.getCultureFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);

        // then - 축마다 (1) 하위 가치 목록이 CultureAxis 정의와 같고 (2) 게이지 %가 그 가치들의 평균과 같아야 한다.
        assertThat(response.axesDetails()).hasSize(CultureAxis.values().length);

        for (CultureAxis axis : CultureAxis.values()) {
            AxisDetailDTO detail = response.axesDetails().stream()
                    .filter(it -> it.name().equals(axis.getDisplayName()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("축이 누락되었습니다: " + axis.getDisplayName()));

            assertThat(detail.subTraits())
                    .as("%s 축의 하위 가치 목록", axis.getDisplayName())
                    .extracting(SubTraitDTO::name)
                    .containsExactlyElementsOf(axis.getTraits());

            int expectedPercentage = (int) Math.round(axis.getTraits().stream()
                    .mapToDouble(extractedCulturefit::get)
                    .average()
                    .orElseThrow() * 100);
            assertThat(detail.percentage())
                    .as("%s 축 게이지 %%는 하위 가치 평균과 같아야 한다", axis.getDisplayName())
                    .isEqualTo(expectedPercentage);
        }

        // 쾌락은 자율·혁신(A) 그룹이므로 성과·영향 하위에 나타나면 안 된다.
        AxisDetailDTO selfEnhancement = response.axesDetails().stream()
                .filter(it -> it.name().equals(CultureAxis.SELF_ENHANCEMENT.getDisplayName()))
                .findFirst()
                .orElseThrow();
        assertThat(selfEnhancement.subTraits()).extracting(SubTraitDTO::name).doesNotContain("쾌락");
    }

    private void givenValidatedMatchResult(MatchResult matchResult) {
        given(matchResultService.getValidatedMatchResult(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .willReturn(matchResult);
    }
}

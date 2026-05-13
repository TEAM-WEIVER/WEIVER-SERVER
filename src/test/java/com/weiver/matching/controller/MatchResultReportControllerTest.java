package com.weiver.matching.controller;

import com.weiver.analysis.dto.response.AxisDetailDTO;
import com.weiver.analysis.dto.response.CompetencyDetailDTO;
import com.weiver.analysis.dto.response.CultureAxisDTO;
import com.weiver.analysis.dto.response.CultureFitSummaryDTO;
import com.weiver.analysis.dto.response.SubTraitDTO;
import com.weiver.global.common.UserRole;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.cookie.CookieProvider;
import com.weiver.global.security.jwt.JwtAuthenticationFilter;
import com.weiver.global.security.jwt.JwtTokenProvider;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.matching.dto.response.ApplicantCardResponseDTO;
import com.weiver.matching.dto.response.CardDetailDTO;
import com.weiver.matching.dto.response.DocumentTabSummaryDTO;
import com.weiver.matching.dto.response.InterviewScriptDTO;
import com.weiver.matching.dto.response.MajorCareerDTO;
import com.weiver.matching.dto.response.PortfolioDetailDTO;
import com.weiver.matching.dto.response.ProfileDetailDTO;
import com.weiver.matching.dto.response.SkillFitSummaryDTO;
import com.weiver.matching.dto.response.SummaryCardResponseDTO;
import com.weiver.matching.service.MatchResultReportService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(MatchResultReportController.class)
@AutoConfigureMockMvc(addFilters = false)
class MatchResultReportControllerTest {

    private static final Long JD_ID = 1L;
    private static final String APPLICANT_PUBLIC_ID = "applicant-8f4a2c1e";
    private static final String COMPANY_PUBLIC_ID = "company-1";
    private static final String BASE_URL = "/api/job-postings/{jdId}/applicants/{applicantPublicId}/reports";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MatchResultReportService matchResultReportService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockitoBean
    private CookieProvider cookieProvider;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("카드 요약 조회 성공: 프로필, 카드 정보, 메모를 응답한다")
    void getCardSummary_Success() throws Exception {
        // given
        ApplicantCardResponseDTO response = new ApplicantCardResponseDTO(
                new ProfileDetailDTO(10L, "이현우", "010-1234-5678", "applicant@test.com", "https://image.test/profile.png", "백엔드 개발자"),
                new CardDetailDTO(86, "협업 경험이 좋습니다.", "자율 혁신형", List.of("Java", "Spring Boot")),
                "서류 우선 검토 대상"
        );

        given(matchResultReportService.getCardSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .willReturn(response);

        // when & then
        mockMvc.perform(get(BASE_URL + "/card-summary", JD_ID, APPLICANT_PUBLIC_ID)
                        .with(companyAuth())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.profile.name").value("이현우"))
                .andExpect(jsonPath("$.data.card.skillScore").value(86))
                .andExpect(jsonPath("$.data.card.skillTags[0]").value("Java"))
                .andExpect(jsonPath("$.data.memo").value("서류 우선 검토 대상"));

        verify(matchResultReportService).getCardSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);
    }

    @Test
    @DisplayName("AI 요약 조회 성공: AI 요약과 주요 경력을 응답한다")
    void getAiSummary_Success() throws Exception {
        // given
        SummaryCardResponseDTO response = new SummaryCardResponseDTO(
                "공고 요구사항과 지원자의 백엔드 경험이 잘 맞습니다.",
                List.of(new MajorCareerDTO(1L, "위버", "백엔드 개발자", "정규직",
                        LocalDate.of(2023, 1, 1), null, "매칭 API 개발"))
        );

        given(matchResultReportService.getSummaryCard(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .willReturn(response);

        // when & then
        mockMvc.perform(get(BASE_URL + "/ai-summary", JD_ID, APPLICANT_PUBLIC_ID)
                        .with(companyAuth())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSummary").value("공고 요구사항과 지원자의 백엔드 경험이 잘 맞습니다."))
                .andExpect(jsonPath("$.data.majorCareers[0].companyName").value("위버"))
                .andExpect(jsonPath("$.data.majorCareers[0].endDate").doesNotExist());

        verify(matchResultReportService).getSummaryCard(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);
    }

    @Test
    @DisplayName("스킬 적합도 조회 성공: 역량 분석, 요약, 기술 태그, 매칭률을 응답한다")
    void getSkillFitSummary_Success() throws Exception {
        // given
        SkillFitSummaryDTO response = new SkillFitSummaryDTO(
                List.of(new CompetencyDetailDTO("문제 해결력", 92)),
                "우선순위 역량과 분석 결과가 잘 일치합니다.",
                List.of("Java", "JPA"),
                87.5f
        );

        given(matchResultReportService.getSkillFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .willReturn(response);

        // when & then
        mockMvc.perform(get(BASE_URL + "/skill-fit", JD_ID, APPLICANT_PUBLIC_ID)
                        .with(companyAuth())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSkillAnalysis[0].name").value("문제 해결력"))
                .andExpect(jsonPath("$.data.aiSkillAnalysis[0].percentage").value(92))
                .andExpect(jsonPath("$.data.skillTags[1]").value("JPA"))
                .andExpect(jsonPath("$.data.matchingRate").value(87.5));

        verify(matchResultReportService).getSkillFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);
    }

    @Test
    @DisplayName("컬처 적합도 조회 성공: 상위 축과 축 상세 정보를 응답한다")
    void getCultureFitSummary_Success() throws Exception {
        // given
        CultureFitSummaryDTO response = new CultureFitSummaryDTO(
                "높은 매칭률",
                "자율 혁신형",
                List.of(new CultureAxisDTO("자율·혁신", 91), new CultureAxisDTO("관계·공동체", 84)),
                "자율적인 문제 해결 문화와 잘 맞습니다.",
                List.of(new AxisDetailDTO("자율·혁신", 91, List.of(new SubTraitDTO("자기방향", 88))))
        );

        given(matchResultReportService.getCultureFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .willReturn(response);

        // when & then
        mockMvc.perform(get(BASE_URL + "/culture-fit", JD_ID, APPLICANT_PUBLIC_ID)
                        .with(companyAuth())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.matchStatus").value("높은 매칭률"))
                .andExpect(jsonPath("$.data.topTwoAxes[0].name").value("자율·혁신"))
                .andExpect(jsonPath("$.data.axesDetails[0].subTraits[0].percentage").value(88));

        verify(matchResultReportService).getCultureFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);
    }

    @Test
    @DisplayName("제출 서류 요약 조회 성공: 포트폴리오와 면접 스크립트를 응답한다")
    void getDocumentTabSummary_Success() throws Exception {
        // given
        DocumentTabSummaryDTO response = new DocumentTabSummaryDTO(
                new PortfolioDetailDTO("https://file.test/portfolio.pdf", "https://github.com/weiver", null, null),
                List.of(new InterviewScriptDTO("JPA N+1 문제를 어떻게 해결했나요?", "fetch join을 활용했습니다.")),
                List.of(new InterviewScriptDTO("갈등 상황을 어떻게 해결했나요?", "근거를 정리해 합의했습니다."))
        );

        given(matchResultReportService.getDocumentTabSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .willReturn(response);

        // when & then
        mockMvc.perform(get(BASE_URL + "/document-summary", JD_ID, APPLICANT_PUBLIC_ID)
                        .with(companyAuth())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolio.portfolioFileUrl").value("https://file.test/portfolio.pdf"))
                .andExpect(jsonPath("$.data.portfolio.urlTech").doesNotExist())
                .andExpect(jsonPath("$.data.techInterviewScripts[0].question").value("JPA N+1 문제를 어떻게 해결했나요?"))
                .andExpect(jsonPath("$.data.cultureInterviewScripts[0].answer").value("근거를 정리해 합의했습니다."));

        verify(matchResultReportService).getDocumentTabSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);
    }

    @Test
    @DisplayName("엣지 케이스: 인증 정보가 없으면 401 UNAUTHORIZED를 응답하고 서비스를 호출하지 않는다")
    void getReport_Unauthorized() throws Exception {
        // when & then
        mockMvc.perform(get(BASE_URL + "/card-summary", JD_ID, APPLICANT_PUBLIC_ID)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"));

        verifyNoInteractions(matchResultReportService);
    }

    @Test
    @DisplayName("엣지 케이스: 공고 ID가 숫자가 아니면 400 BAD REQUEST를 응답하고 서비스를 호출하지 않는다")
    void getReport_InvalidJdIdType() throws Exception {
        // when & then
        mockMvc.perform(get(BASE_URL + "/skill-fit", "not-number", APPLICANT_PUBLIC_ID)
                        .with(companyAuth())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("BIND_FAILED"));

        verifyNoInteractions(matchResultReportService);
    }

    @Test
    @DisplayName("엣지 케이스: 접근 권한이 없는 매칭 결과이면 403 FORBIDDEN을 응답한다")
    void getReport_ForbiddenMatchResult() throws Exception {
        // given
        given(matchResultReportService.getCultureFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .willThrow(new BusinessException(ErrorCode.FORBIDDEN));

        // when & then
        mockMvc.perform(get(BASE_URL + "/culture-fit", JD_ID, APPLICANT_PUBLIC_ID)
                        .with(companyAuth())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"));

        verify(matchResultReportService).getCultureFitSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID);
    }

    @Test
    @DisplayName("엣지 케이스: 포트폴리오와 면접 스크립트가 비어 있어도 빈 값 그대로 응답한다")
    void getDocumentTabSummary_EmptyContent() throws Exception {
        // given
        DocumentTabSummaryDTO response = new DocumentTabSummaryDTO(
                new PortfolioDetailDTO(null, null, null, null),
                List.of(),
                List.of()
        );

        given(matchResultReportService.getDocumentTabSummary(JD_ID, APPLICANT_PUBLIC_ID, COMPANY_PUBLIC_ID))
                .willReturn(response);

        // when & then
        mockMvc.perform(get(BASE_URL + "/document-summary", JD_ID, APPLICANT_PUBLIC_ID)
                        .with(companyAuth())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.portfolio.portfolioFileUrl").doesNotExist())
                .andExpect(jsonPath("$.data.techInterviewScripts").isEmpty())
                .andExpect(jsonPath("$.data.cultureInterviewScripts").isEmpty());
    }

    @Test
    @DisplayName("엣지 케이스: 지원자 publicId에 하이픈, 언더스코어, 점이 포함되어도 그대로 서비스로 전달한다")
    void getReport_ApplicantPublicIdWithUrlSafeSpecialCharactersPassedAsIs() throws Exception {
        // given
        String applicantPublicId = "applicant-8f4a2c1e_test.v1";
        SummaryCardResponseDTO response = new SummaryCardResponseDTO("요약 없음", List.of());

        given(matchResultReportService.getSummaryCard(JD_ID, applicantPublicId, COMPANY_PUBLIC_ID))
                .willReturn(response);

        // when & then
        mockMvc.perform(get(BASE_URL + "/ai-summary", JD_ID, applicantPublicId)
                        .with(companyAuth())
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiSummary").value("요약 없음"))
                .andExpect(jsonPath("$.data.majorCareers").isEmpty());

        verify(matchResultReportService).getSummaryCard(JD_ID, applicantPublicId, COMPANY_PUBLIC_ID);
    }

    private RequestPostProcessor companyAuth() {
        return request -> {
            AuthenticatedPrincipal principal = new AuthenticatedPrincipal(COMPANY_PUBLIC_ID, UserRole.COMPANY);
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    principal,
                    null,
                    List.of(new SimpleGrantedAuthority("ROLE_COMPANY"))
            );
            SecurityContextHolder.getContext().setAuthentication(auth);
            return request;
        };
    }
}

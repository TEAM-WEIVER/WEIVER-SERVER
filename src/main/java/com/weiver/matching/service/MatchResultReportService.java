package com.weiver.matching.service;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.DetailAnalysisReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.dto.response.*;
import com.weiver.analysis.service.ReportService;
import com.weiver.applicant.dto.response.ApplicantProfileDto;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchResultReportService {

    private static final String SKILL_QUESTION_PREFIX = "S_";
    private static final String CULTURE_QUESTION_PREFIX = "C_";

    private final ApplicantService applicantService;
    private final ReportService reportService;
    private final MatchResultService matchResultService;
    private final PortfolioService portfolioService;
    private final WorkExperienceService workExperienceService;
    private final InterviewSessionService interviewSessionService;

    private static final Map<String, String> COMPETENCY_NAMES = Map.of(
            "learning", "성장가능성",
            "adaptability", "대처능력",
            "consistency", "일관성",
            "collaboration", "협업 및 팀워크",
            "problem_solving", "문제해결력",
            "logic", "논리성"
    );

    private static final Map<String, String> CULTURE_AXIS_NAMES = Map.of(
            "openness_to_change", "자율·혁신",
            "self_enhancement", "성과·영향",
            "conservation", "안정·질서",
            "self_transcendence", "관계·공동체"
    );

    public ApplicantCardResponseDTO getCardSummary(Long jdId, String applicantPublicId, String companyPublicId) {

        MatchResult matchResult = matchResultService.getValidatedMatchResult(jdId, applicantPublicId, companyPublicId);

        ApplicantProfileDto profileDto = applicantService.getApplicantProfile(applicantPublicId);
        AnalysisReportDto analysisDto = reportService.getApplicantReport(applicantPublicId);

        ProfileDetailDTO profileDetailDTO = ProfileDetailDTO.of(profileDto.applicant(), profileDto.position());

        CardDetailDTO cardDetail = CardDetailDTO.of(
                matchResult,
                analysisDto.cultureReport(),
                analysisDto.technicalSkillReport()
        );

        return ApplicantCardResponseDTO.of(profileDetailDTO, cardDetail, matchResult.getNote());
    }

    public SummaryCardResponseDTO getSummaryCard(Long jdId, String applicantPublicId, String companyPublicId){

        MatchResult matchResult = matchResultService.getValidatedMatchResult(jdId, applicantPublicId, companyPublicId);
        List<MajorCareerDTO> careerSummary = workExperienceService.getCareerSummary(applicantPublicId);

        return SummaryCardResponseDTO.of(matchResult.getAiSummary(), careerSummary);
    }

    /**
     * 스킬핏 분석 탭 조회 로직
     * */
    public SkillFitSummaryDTO getSkillFitSummary(Long jdId, String applicantPublicId, String companyPublicId){
        MatchResult matchResult = matchResultService.getValidatedMatchResult(jdId, applicantPublicId, companyPublicId);

        DetailAnalysisReport detailAnalysisReport = reportService.getDetailAnalysisReport(applicantPublicId);
        TechnicalSkillReport technicalSkillReport = reportService.getTechnicalSkillReport(applicantPublicId);

        JobPosting jobPosting = matchResult.getJobPosting();
        Float matchingRate = matchResult.getMatchingRate();

        Map<String, Object> skillAnalysisMap = detailAnalysisReport.getSkillAnalysis();

        String aiAbilitySummary = generateAiAbilitySummary(jobPosting.getCompetencyPriorities(), skillAnalysisMap);

        List<CompetencyDetailDTO> competencyDetails = extractCompetencyDetails(skillAnalysisMap);

        return SkillFitSummaryDTO.of(
                competencyDetails,
                aiAbilitySummary,
                technicalSkillReport.getSkillTags(),
                matchingRate
        );
    }

    /**
     * 컬처핏 분석 탭 조회 로직
     * */
    public CultureFitSummaryDTO getCultureFitSummary(Long jdId, String applicantPublicId, String companyPublicId) {
        MatchResult matchResult = matchResultService.getValidatedMatchResult(jdId, applicantPublicId, companyPublicId);

        DetailAnalysisReport detailAnalysisReport = reportService.getDetailAnalysisReport(applicantPublicId);
        CultureReport cultureReport = reportService.getCultureReport(applicantPublicId);

        String culturefitStyle = cultureReport.getCulturefitStyles().getDescription();
        String aiSummary = matchResult.getAiSummary();

        String matchStatus = matchResult.getMatchingRate() >= 80 ? "높은 매칭률" : "보통 매칭률";

        Map<String, Object> cultureAnalysisMap = detailAnalysisReport.getCultureAnalysis();

        List<AxisDetailDTO> axesDetails = buildAxesDetails(cultureAnalysisMap);

        List<CultureAxisDTO> topTwoAxes = extractTopTwoAxes(axesDetails);

        return CultureFitSummaryDTO.of(
                matchStatus,
                culturefitStyle,
                topTwoAxes,
                aiSummary,
                axesDetails
        );
    }

    /**
     * 제출 서류 (면접 스크립트) 탭 조회 로직
     * */
    public DocumentTabSummaryDTO getDocumentTabSummary(Long jdId, String applicantPublicId, String companyPublicId) {
        matchResultService.getValidatedMatchResult(jdId, applicantPublicId, companyPublicId);

        PortfolioDetailDTO portfolioDetailDTO;
        try {
            portfolioDetailDTO = portfolioService.getApplicantPortfolio(applicantPublicId);
        } catch (BusinessException e) {
            if (e.getCode() == ErrorCode.PORTFOLIO_NOT_FOUND) {
                portfolioDetailDTO = new PortfolioDetailDTO(null, null, null, null);
            } else {
                throw e;
            }
        }

        List<InterviewTurnDTO> interviewTurns = interviewSessionService.getLatestInterviewTurns(applicantPublicId);
        List<InterviewTurnDTO> techScripts = filterInterviewTurnsByQuestionPrefix(interviewTurns, SKILL_QUESTION_PREFIX);
        List<InterviewTurnDTO> cultureScripts = filterInterviewTurnsByQuestionPrefix(interviewTurns, CULTURE_QUESTION_PREFIX);

        return DocumentTabSummaryDTO.of(
                portfolioDetailDTO,
                techScripts,
                cultureScripts
        );

    }

    private List<InterviewTurnDTO> filterInterviewTurnsByQuestionPrefix(
            List<InterviewTurnDTO> interviewTurns,
            String questionPrefix
    ) {
        if (interviewTurns == null) {
            return List.of();
        }

        return interviewTurns.stream()
                .filter(Objects::nonNull)
                .filter(turn -> turn.questionCode() != null)
                .filter(turn -> turn.questionCode().startsWith(questionPrefix))
                .toList();
    }


    /**
     * 4개 상위 축과 각각에 속하는 하위 가치(10개)를 조립합니다.
     */
    private List<AxisDetailDTO> buildAxesDetails(Map<String, Object> cultureAnalysisMap) {
        if (cultureAnalysisMap == null) return new ArrayList<>();

        try {
            Map<String, Object> cultureAxis = (Map<String, Object>) cultureAnalysisMap.get("culture_axis");
            Map<String, Object> extractedTraits = (Map<String, Object>) cultureAnalysisMap.get("extracted_culturefit");

            if (cultureAxis == null || extractedTraits == null) return new ArrayList<>();

            List<AxisDetailDTO> details = new ArrayList<>();

            // 1. 자율·혁신 (Openness to change) -> 하위: 자기방향, 자극
            details.add(new AxisDetailDTO(
                    CULTURE_AXIS_NAMES.get("openness_to_change"),
                    getPercentage(cultureAxis, "openness_to_change"),
                    List.of(
                            new SubTraitDTO("자기방향", getPercentage(extractedTraits, "자기방향")),
                            new SubTraitDTO("자극", getPercentage(extractedTraits, "자극"))
                    )
            ));

            // 2. 성과·영향 (Self-enhancement) -> 하위: 성취, 권력, 쾌락
            details.add(new AxisDetailDTO(
                    CULTURE_AXIS_NAMES.get("self_enhancement"),
                    getPercentage(cultureAxis, "self_enhancement"),
                    List.of(
                            new SubTraitDTO("성취", getPercentage(extractedTraits, "성취")),
                            new SubTraitDTO("권력", getPercentage(extractedTraits, "권력")),
                            new SubTraitDTO("쾌락", getPercentage(extractedTraits, "쾌락"))
                    )
            ));

            // 3. 안정·질서 (Conservation) -> 하위: 안전, 순응, 전통
            details.add(new AxisDetailDTO(
                    CULTURE_AXIS_NAMES.get("conservation"),
                    getPercentage(cultureAxis, "conservation"),
                    List.of(
                            new SubTraitDTO("안전", getPercentage(extractedTraits, "안전")),
                            new SubTraitDTO("순응", getPercentage(extractedTraits, "순응")),
                            new SubTraitDTO("전통", getPercentage(extractedTraits, "전통"))
                    )
            ));

            // 4. 관계·공동체 (Self-transcendence) -> 하위: 호의, 보편주의
            details.add(new AxisDetailDTO(
                    CULTURE_AXIS_NAMES.get("self_transcendence"),
                    getPercentage(cultureAxis, "self_transcendence"),
                    List.of(
                            new SubTraitDTO("호의", getPercentage(extractedTraits, "호의")),
                            new SubTraitDTO("보편주의", getPercentage(extractedTraits, "보편주의"))
                    )
            ));

            return details;

        }catch (ClassCastException | NullPointerException e) {
            log.error("컬처핏 상세 데이터 파싱 실패 - 잘못된 데이터 타입 또는 구조 (ClassCastException/NullPointerException)", e);
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("컬처핏 상세 데이터 조립 중 예상치 못한 에러 발생", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * AI 역량 평가 차트용 데이터 추출기
     */
    private List<CompetencyDetailDTO> extractCompetencyDetails(Map<String, Object> evaluation) {
        List<CompetencyDetailDTO> details = new ArrayList<>();
        if (evaluation == null) return details;

        try {

            Map<String, Object> criteriaSummary = (Map<String, Object>) evaluation.get("criteria_summary");            if (criteriaSummary == null) return details;

            // 전체 6개 역량을 돌면서 DTO로 변환
            for (Map.Entry<String, Object> entry : criteriaSummary.entrySet()) {
                String competencyKey = entry.getKey();
                String koreanName = COMPETENCY_NAMES.getOrDefault(competencyKey, competencyKey);

                Map<String, Object> competencyData = (Map<String, Object>) entry.getValue();
                Object avgScoreObj = competencyData.get("average_score");

                if (avgScoreObj instanceof Number) {
                    double score = ((Number) avgScoreObj).doubleValue();
                    int percentage = (int) Math.round((score / 5.0) * 100);

                    details.add(new CompetencyDetailDTO(koreanName, percentage));
                }
            }
        } catch (ClassCastException | NullPointerException e) {
            log.error("AI 역량 평가 차트 데이터 파싱 실패 (ClassCastException/NullPointerException)", e);
        } catch (Exception e) {
            log.error("AI 역량 평가 차트 데이터 추출 중 예상치 못한 에러 발생", e);
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }

        return details;
    }

    /**
     * AI 역량 평가 맞춤형 멘트 생성기
     */
    private String generateAiAbilitySummary(List<String> priorities, Map<String, Object> evaluation) {
        if (priorities == null || priorities.isEmpty() || evaluation == null) {
            return "우선순위 역량 평가 데이터를 분석할 수 없습니다.";
        }

        try {
            Map<String, Object> criteriaSummary = (Map<String, Object>) evaluation.get("criteria_summary");

            List<CompetencyMatch> matches = new ArrayList<>();

            // 공고 우선순위를 돌면서 지원자의 점수 매핑
            for (int i = 0; i < priorities.size(); i++) {
                String priorityKey = priorities.get(i).toLowerCase();

                if (criteriaSummary != null && criteriaSummary.containsKey(priorityKey)) {
                    Map<String, Object> competencyData = (Map<String, Object>) criteriaSummary.get(priorityKey);
                    Object avgScoreObj = competencyData.get("average_score");

                    if (avgScoreObj instanceof Number) {
                        double score = ((Number) avgScoreObj).doubleValue();
                        int percentage = (int) Math.round((score / 5.0) * 100);

                        matches.add(new CompetencyMatch(i + 1, COMPETENCY_NAMES.getOrDefault(priorityKey, priorityKey), percentage));
                    }
                }
            }

            if (matches.isEmpty()) {
                return "우선순위 역량과 일치하는 분석 결과가 없습니다.";
            }

            matches.sort((a, b) -> Integer.compare(b.percentage(), a.percentage()));

            List<CompetencyMatch> topMatches = matches.stream().limit(3).toList();

            List<CompetencyMatch> sortedForText = topMatches.stream()
                    .sorted(Comparator.comparingInt(CompetencyMatch::rank))
                    .toList();

            String rankStr = sortedForText.stream()
                    .map(m -> m.rank() + "순위")
                    .collect(Collectors.joining(", "));

            String detailStr = sortedForText.stream()
                    .map(m -> m.name() + " " + m.percentage() + "%")
                    .collect(Collectors.joining(", "));

            return String.format("우선순위 역량 중 %s 역량이 %s로 일치합니다.", rankStr, detailStr);

        } catch (ClassCastException | NullPointerException e) {
            log.error("AI 역량 평가 멘트 생성 파싱 실패 (ClassCastException/NullPointerException)", e);
            return "역량 평가 분석 중 데이터 형식 오류가 발생했습니다.";
        } catch (Exception e) {
            log.error("AI 역량 평가 멘트 생성 중 예상치 못한 에러 발생", e);
            return "역량 평가 분석 중 서버 오류가 발생했습니다.";
        }
    }

    /**
     * 조립된 4개의 축 중에서 점수가 가장 높은 상위 2개를 추출합니다.
     */
    private List<CultureAxisDTO> extractTopTwoAxes(List<AxisDetailDTO> axesDetails) {
        return axesDetails.stream()
                .sorted((a, b) -> Integer.compare(b.percentage(), a.percentage())) // 내림차순 정렬
                .limit(2) // 상위 2개 자르기
                .map(detail -> new CultureAxisDTO(detail.name(), detail.percentage()))
                .toList();
    }

    /**
     * Map에서 값을 꺼내 퍼센트(int)로 변환하는 헬퍼 메서드
     * 예: 0.71 -> 71
     */
    private int getPercentage(Map<String, Object> map, String key) {
        if (map.containsKey(key) && map.get(key) instanceof Number num) {
            return (int) Math.round(num.doubleValue() * 100);
        }
        return 0;
    }

    private record CompetencyMatch(int rank, String name, int percentage) {}
}

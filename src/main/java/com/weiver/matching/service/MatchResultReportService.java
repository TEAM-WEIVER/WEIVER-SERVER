package com.weiver.matching.service;

import com.weiver.analysis.domain.DetailAnalysisReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.dto.response.AnalysisReportDto;
import com.weiver.analysis.dto.response.CompetencyDetailDTO;
import com.weiver.analysis.service.ReportService;
import com.weiver.applicant.dto.response.ApplicantProfileDto;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.applicant.service.WorkExperienceService;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.dto.response.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchResultReportService {

    private final ApplicantService applicantService;
    private final ReportService reportService;
    private final MatchResultService matchResultService;
    private final WorkExperienceService workExperienceService;

    private static final Map<String, String> COMPETENCY_NAMES = Map.of(
            "learning", "성장가능성",
            "adaptability", "대처능력",
            "consistency", "일관성",
            "collaboration", "협업 및 팀워크",
            "problem_solving", "문제해결력",
            "logic", "논리성"
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

    public SkillFitSummaryDTO getSkillFitSummary(Long jdId, String applicantPublicId, String companyPublicId){
        MatchResult matchResult = matchResultService.getValidatedMatchResult(jdId, applicantPublicId, companyPublicId);
        DetailAnalysisReport detailAnalysisReport = reportService.getValidatedDetailAnalysisReport(jdId, applicantPublicId, companyPublicId);
        TechnicalSkillReport technicalSkillReport = reportService.getValidatedTechnicalSkillReport(jdId, applicantPublicId, companyPublicId);

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
        } catch (Exception e) {
            log.error("AI 역량 평가 차트 데이터 추출 중 에러 발생", e);
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

                        // 💡 상수로 빼둔 딕셔너리 재사용
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

        } catch (Exception e) {
            log.error("AI 역량 평가 멘트 생성 중 에러 발생", e);
            return "역량 평가 분석 중 오류가 발생했습니다.";
        }
    }

    private record CompetencyMatch(int rank, String name, int percentage) {}
}
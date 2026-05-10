package com.weiver.matching.service;

import com.weiver.analysis.dto.response.AnalysisReportDto;
import com.weiver.analysis.service.ReportService;
import com.weiver.applicant.dto.response.ApplicantProfileDto;
import com.weiver.applicant.service.ApplicantService;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.dto.response.ApplicantCardResponseDTO;
import com.weiver.matching.dto.response.CardDetailDTO;
import com.weiver.matching.dto.response.ProfileDetailDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class MatchResultReportService {

    private final ApplicantService applicantService;
    private final ReportService reportService;
    private final MatchResultService matchResultService;

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
}

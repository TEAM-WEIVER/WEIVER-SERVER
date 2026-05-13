package com.weiver.analysis.service;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.DetailAnalysisReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.dto.response.AnalysisReportDto;
import com.weiver.analysis.repository.CultureReportRepository;
import com.weiver.analysis.repository.DetailAnalysisReportRepository;
import com.weiver.analysis.repository.TechnicalSkillReportRepository;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ApplicantRepository applicantRepository;
    private final DetailAnalysisReportRepository detailAnalysisReportRepository;
    private final CultureReportRepository cultureReportRepository;
    private final TechnicalSkillReportRepository technicalSkillReportRepository;

    /**
     * 지원자 리포트 - 카드 정보 조회
     * @param applicantPublicId 지원자 고유 ID
     */
    public AnalysisReportDto getApplicantReport(String applicantPublicId) {

        Applicant applicant = applicantRepository.findByPublicId(applicantPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        CultureReport cultureReport = cultureReportRepository.findByApplicant(applicant)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND, "컬처핏 분석 데이터가 존재하지 않습니다."));

        TechnicalSkillReport technicalSkillReport = technicalSkillReportRepository.findByApplicant(applicant)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPORT_NOT_FOUND, "스킬핏 분석 데이터가 존재하지 않습니다."));

        return new AnalysisReportDto(cultureReport, technicalSkillReport);
    }

    /**
     * 매칭 결과 검증 - 해당 공고에 대한 매칭 결과가 존재하는지 검증 (DetailAnalysisReport 존재 여부로 검증)
     * */
    public DetailAnalysisReport getDetailAnalysisReport(String applicantPublicId) {
        return detailAnalysisReportRepository.findByApplicant_PublicId(applicantPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
    }

    public TechnicalSkillReport getTechnicalSkillReport(String applicantPublicId) {
        return technicalSkillReportRepository.findByApplicant_PublicId(applicantPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
    }

    public CultureReport getCultureReport(String applicantPublicId) {
        return cultureReportRepository.findByApplicant_PublicId(applicantPublicId)
                .orElseThrow(() -> new BusinessException(ErrorCode.MATCH_NOT_FOUND));
    }
}
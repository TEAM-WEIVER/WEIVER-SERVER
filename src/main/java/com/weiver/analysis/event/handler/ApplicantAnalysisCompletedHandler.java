package com.weiver.analysis.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.event.dto.ApplicantAnalysisCompletedData;
import com.weiver.analysis.repository.CultureReportRepository;
import com.weiver.analysis.repository.TechnicalSkillReportRepository;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.Arrays;
import java.util.List;

@Component
@RequiredArgsConstructor
public class ApplicantAnalysisCompletedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final ApplicantRepository applicantRepository;
    private final TechnicalSkillReportRepository technicalSkillReportRepository;
    private final CultureReportRepository cultureReportRepository;

    @Override
    public EventType support() {
        return EventType.APPLICANT_ANALYSIS_COMPLETED;
    }

    @Override
    @Transactional
    public void handle(EventEnvelope<JsonNode> envelope) {
        // AI 분석 결과를 지원자 기준으로 멱등 저장한다.
        ApplicantAnalysisCompletedData data = objectMapper.convertValue(
                envelope.data(),
                ApplicantAnalysisCompletedData.class
        );
        validate(data);

        Applicant applicant = applicantRepository.findById(data.applicantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        upsertTechnicalSkillReport(applicant, data);
        upsertCultureReportIfPresent(applicant, data);
    }

    private void validate(ApplicantAnalysisCompletedData data) {
        if (data.applicantId() == null) {
            throw new NonRetryableEventException("applicant_id is required");
        }
        if (!StringUtils.hasText(data.job())) {
            throw new NonRetryableEventException("job is required");
        }
        if (!StringUtils.hasText(data.role())) {
            throw new NonRetryableEventException("role is required");
        }
    }

    /**
     * 기술 태그와 직무/역할 분석 결과를 있으면 갱신하고 없으면 새로 저장한다.
     */
    private void upsertTechnicalSkillReport(Applicant applicant, ApplicantAnalysisCompletedData data) {
        technicalSkillReportRepository.findByApplicant_ApplicantId(applicant.getApplicantId())
                .ifPresentOrElse(
                        report -> report.updateAnalysis(safeList(data.skillTags()), data.job(), data.role()),
                        () -> technicalSkillReportRepository.save(TechnicalSkillReport.builder()
                                .applicant(applicant)
                                .skillTags(safeList(data.skillTags()))
                                .job(data.job())
                                .role(data.role())
                                .build())
                );
    }

    /**
     * 컬처핏 결과가 payload에 포함된 경우에만 컬처 리포트를 upsert한다.
     */
    private void upsertCultureReportIfPresent(Applicant applicant, ApplicantAnalysisCompletedData data) {
        CulturefitStyle culturefitStyle = parseCulturefitStyle(data.culturefitStyle());
        List<String> culturefitTags = safeList(data.culturefitTags());

        if (culturefitStyle == null && culturefitTags.isEmpty()) {
            return;
        }

        cultureReportRepository.findByApplicant_ApplicantId(applicant.getApplicantId())
                .ifPresentOrElse(
                        report -> report.updateAnalysis(culturefitStyle, culturefitTags),
                        () -> cultureReportRepository.save(CultureReport.builder()
                                .applicant(applicant)
                                .culturefitStyles(culturefitStyle)
                                .culturefitTag(culturefitTags)
                                .build())
                );
    }

    private CulturefitStyle parseCulturefitStyle(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }

        return Arrays.stream(CulturefitStyle.values())
                .filter(style -> style.name().equals(value) || style.getDescription().equals(value))
                .findFirst()
                .orElseThrow(() -> new NonRetryableEventException("Unsupported culturefit_style: " + value));
    }

    private List<String> safeList(List<String> values) {
        return values != null ? values : List.of();
    }
}

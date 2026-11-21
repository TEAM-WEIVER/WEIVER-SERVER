package com.weiver.analysis.service;

import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.event.dto.ApplicantAnalysisCompletedData;
import com.weiver.analysis.repository.CultureReportRepository;
import com.weiver.analysis.repository.TechnicalSkillReportRepository;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAnalysisSubmission;
import com.weiver.applicant.repository.ApplicantAnalysisSubmissionRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * 지원자 분석 완료 결과를 현재 제출 건에 반영한다.
 *
 * <p>지원자 행을 먼저 비관적 쓰기 잠금으로 조회하고(제출 경로와 같은 순서), 다음이 모두 맞을 때만 반영한다:
 * 지원자의 현재 제출 버전 = 결과의 submission_version, 그 버전의 제출 건 존재, 제출 건의 요청 event_id = correlation_id,
 * 제출 건 상태 REQUESTED. 하나라도 어긋나면(과거 버전, 다른 요청, 중복 완료, 실패 뒤 도착 등) 아무것도 바꾸지 않고
 * 로그만 남긴다. 예외를 던지지 않으므로 DLQ로 가지 않는다.
 */
@Slf4j
@Service
@Transactional
@RequiredArgsConstructor
public class ApplicantAnalysisResultService {

    private final ApplicantRepository applicantRepository;
    private final ApplicantAnalysisSubmissionRepository submissionRepository;
    private final TechnicalSkillReportRepository technicalSkillReportRepository;
    private final CultureReportRepository cultureReportRepository;

    /**
     * @param correlationId 결과 봉투의 correlation_id(= 요청 봉투 event_id)
     * @param data          핸들러가 필수값을 검증한 결과 data
     */
    public void applyCompletion(String correlationId, ApplicantAnalysisCompletedData data) {
        Applicant applicant = applicantRepository.findWithLockByApplicantId(data.applicantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        Optional<ApplicantAnalysisSubmission> target = findApplicableSubmission(applicant, correlationId, data);
        if (target.isEmpty()) {
            return;
        }
        ApplicantAnalysisSubmission submission = target.get();

        if (data.isFailed()) {
            ApplicantAnalysisCompletedData.ErrorData error = data.error();
            submission.markAnalysisFailed(
                    error != null ? error.stage() : null,
                    error != null ? error.code() : null,
                    OffsetDateTime.now()
            );
            return;
        }

        upsertTechnicalSkillReport(applicant, data);
        upsertCultureReportIfPresent(applicant, data);
        submission.markCompleted(data.portfolioStatus(), data.portfolioIssue(), OffsetDateTime.now());
    }

    /** 반영 조건을 모두 만족하는 현재 제출 건. 어긋나면 이유를 로그로 남기고 빈 값을 돌려준다. */
    private Optional<ApplicantAnalysisSubmission> findApplicableSubmission(
            Applicant applicant, String correlationId, ApplicantAnalysisCompletedData data) {
        Integer currentVersion = applicant.getCurrentSubmissionVersion();
        if (!Objects.equals(currentVersion, data.submissionVersion())) {
            logIgnored(applicant, data, "NOT_CURRENT_VERSION(current=" + currentVersion + ")");
            return Optional.empty();
        }

        Optional<ApplicantAnalysisSubmission> found =
                submissionRepository.findByApplicantAndSubmissionVersion(applicant, currentVersion);
        if (found.isEmpty()) {
            logIgnored(applicant, data, "SUBMISSION_NOT_FOUND");
            return Optional.empty();
        }

        ApplicantAnalysisSubmission submission = found.get();
        if (!submission.isResultOf(correlationId)) {
            logIgnored(applicant, data, "CORRELATION_ID_MISMATCH");
            return Optional.empty();
        }
        if (!submission.isRequested()) {
            logIgnored(applicant, data, "NOT_REQUESTED(status=" + submission.getStatus() + ")");
            return Optional.empty();
        }
        return found;
    }

    private void logIgnored(Applicant applicant, ApplicantAnalysisCompletedData data, String reason) {
        log.warn("지원자 분석 완료 결과를 반영하지 않는다. applicantId={}, submissionVersion={}, outcome={}, reason={}",
                applicant.getApplicantId(), data.submissionVersion(), data.outcome(), reason);
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

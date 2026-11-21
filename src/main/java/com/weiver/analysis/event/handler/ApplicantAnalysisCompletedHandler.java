package com.weiver.analysis.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.event.dto.ApplicantAnalysisCompletedData;
import com.weiver.analysis.service.ApplicantAnalysisResultService;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 지원자 분석 완료 이벤트를 받아 payload 필수값을 검증하고, correlation_id와 함께 완료 처리 서비스에 넘긴다.
 * 트랜잭션과 반영 조건 판단은 {@link ApplicantAnalysisResultService}가 맡는다.
 */
@Component
@RequiredArgsConstructor
public class ApplicantAnalysisCompletedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final ApplicantAnalysisResultService applicantAnalysisResultService;

    @Override
    public EventType support() {
        return EventType.APPLICANT_ANALYSIS_COMPLETED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> envelope) {
        ApplicantAnalysisCompletedData data = objectMapper.convertValue(
                envelope.data(),
                ApplicantAnalysisCompletedData.class
        );
        validate(data);

        applicantAnalysisResultService.applyCompletion(envelope.correlationId(), data);
    }

    private void validate(ApplicantAnalysisCompletedData data) {
        if (data.applicantId() == null) {
            throw new NonRetryableEventException("applicant_id is required");
        }
        if (data.submissionVersion() == null) {
            throw new NonRetryableEventException("submission_version is required");
        }
        if (!data.hasSupportedOutcome()) {
            throw new NonRetryableEventException("Unsupported outcome: " + data.outcome());
        }
        // 실패 결과에는 job·role이 없으므로 성공일 때만 확인한다.
        if (data.isFailed()) {
            return;
        }
        if (!StringUtils.hasText(data.job())) {
            throw new NonRetryableEventException("job is required");
        }
        if (!StringUtils.hasText(data.role())) {
            throw new NonRetryableEventException("role is required");
        }
        // 잠금·리포트 upsert 전에 끝내도록 컬처핏 스타일 값도 미리 확인한다(비어 있으면 컬처 리포트를 만들지 않으므로 통과).
        if (StringUtils.hasText(data.culturefitStyle())
                && CulturefitStyle.fromNameOrDescription(data.culturefitStyle()).isEmpty()) {
            throw new NonRetryableEventException("Unsupported culturefit_style: " + data.culturefitStyle());
        }
    }
}

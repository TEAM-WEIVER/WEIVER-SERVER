package com.weiver.applicant.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.analysis.event.ApplicantAnalysisEventService;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.event.dto.ApplicantProfileSyncCompletedData;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.metrics.AiRoundtripMetrics;
import com.weiver.global.metrics.type.AiFlow;
import com.weiver.global.metrics.type.AiOutcome;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ApplicantProfileSyncCompletedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final ApplicantRepository applicantRepository;
    private final ApplicantAnalysisEventService applicantAnalysisEventService;
    private final AiRoundtripMetrics aiRoundtripMetrics;

    @Override
    public EventType support() {
        return EventType.APPLICANT_PROFILE_SYNC_COMPLETED;
    }

    @Override
    @Transactional
    public void handle(EventEnvelope<JsonNode> envelope) {
        // AI 서버의 프로필 upsert 결과를 반영하고, 성공 시 다음 분석 요청을 이어서 발행한다.
        ApplicantProfileSyncCompletedData data = objectMapper.convertValue(
                envelope.data(),
                ApplicantProfileSyncCompletedData.class
        );
        validate(data);

        Applicant applicant = applicantRepository.findById(data.applicantId())
                .orElseThrow(() -> new BusinessException(ErrorCode.APPLICANT_NOT_FOUND));

        if (applicant.getProfileSyncStatus() == ProfileSyncStatus.COMPLETED) {
            return;
        }
        // 재수신된 결과는 지연 지표에 다시 넣지 않도록, 요청 중이던 지원자만 기록한다.
        boolean wasRequested = applicant.getProfileSyncStatus() == ProfileSyncStatus.REQUESTED;
        if (Boolean.FALSE.equals(data.synced())) {
            applicant.markProfileSyncFailed();
            recordRoundtrip(wasRequested, AiOutcome.FAILED, applicant);
            return;
        }

        applicant.markProfileSyncCompleted(envelope.occurredAt());
        recordRoundtrip(wasRequested, AiOutcome.COMPLETED, applicant);
        applicantAnalysisEventService.publishApplicantAnalysisRequested(applicant.getApplicantId());
    }

    private void recordRoundtrip(boolean wasRequested, AiOutcome outcome, Applicant applicant) {
        if (wasRequested) {
            aiRoundtripMetrics.record(AiFlow.PROFILE_SYNC, outcome, applicant.getProfileSyncRequestedAt());
        }
    }

    private void validate(ApplicantProfileSyncCompletedData data) {
        if (data.applicantId() == null) {
            throw new NonRetryableEventException("applicant_id is required");
        }
        if (data.synced() == null) {
            throw new NonRetryableEventException("synced is required");
        }
    }
}

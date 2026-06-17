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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ApplicantProfileSyncCompletedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final ApplicantRepository applicantRepository;
    private final ApplicantAnalysisEventService applicantAnalysisEventService;

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
        if (Boolean.FALSE.equals(data.synced())) {
            applicant.markProfileSyncFailed();
            return;
        }

        applicant.markProfileSyncCompleted(envelope.occurredAt());
        applicantAnalysisEventService.publishApplicantAnalysisRequested(applicant.getApplicantId());
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

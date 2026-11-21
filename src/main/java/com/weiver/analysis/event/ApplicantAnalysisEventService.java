package com.weiver.analysis.event;

import com.weiver.analysis.event.dto.ApplicantAnalysisRequestedData;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicantAnalysisEventService {

    private final DomainEventPublisher domainEventPublisher;

    /**
     * 프로필 제출 건의 분석 요청 이벤트를 트랜잭션 커밋 뒤에 발행한다.
     *
     * @param eventId 제출 건에 저장한 요청 event_id. 완료 이벤트의 correlation_id와 맞춰 보는 값이다.
     */
    public void publishApplicantAnalysisRequested(String eventId, ApplicantAnalysisRequestedData data) {
        EventEnvelope<ApplicantAnalysisRequestedData> envelope = EventEnvelope.request(
                EventType.APPLICANT_ANALYSIS_REQUESTED,
                data,
                eventId
        );

        domainEventPublisher.publishAfterCommit(envelope);
    }
}

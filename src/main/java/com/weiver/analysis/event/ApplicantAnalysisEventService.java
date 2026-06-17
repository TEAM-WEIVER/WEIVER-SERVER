package com.weiver.analysis.event;

import com.weiver.analysis.event.dto.ApplicantAnalysisRequestedData;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.event.util.EventIds;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class ApplicantAnalysisEventService {

    private final DomainEventPublisher domainEventPublisher;

    /**
     * 프로필 동기화가 끝난 지원자의 분석 요청 이벤트를 발행한다.
     */
    public void publishApplicantAnalysisRequested(Long applicantId) {
        EventEnvelope<ApplicantAnalysisRequestedData> envelope = EventEnvelope.request(
                EventType.APPLICANT_ANALYSIS_REQUESTED,
                new ApplicantAnalysisRequestedData(applicantId),
                EventIds.newEventId()
        );

        domainEventPublisher.publish(envelope);
    }
}

package com.weiver.applicant.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 과거 프로필 동기화 완료(applicant.profile.sync.completed) 이벤트를 받아 버린다.
 *
 * <p>AI 서버가 동기화와 분석을 분석 요청 하나로 합쳐 더는 이 이벤트를 보내지 않는다. 큐에 남은 과거 이벤트가
 * 알 수 없는 이벤트 타입 오류로 DLQ에 가지 않도록 핸들러만 남기고, 지원자·제출 건 상태, 잠금, 지연 지표는 건드리지 않는다.
 */
@Slf4j
@Component
public class ApplicantProfileSyncCompletedHandler implements DomainEventHandler {

    @Override
    public EventType support() {
        return EventType.APPLICANT_PROFILE_SYNC_COMPLETED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> envelope) {
        JsonNode data = envelope.data();
        log.info("과거 프로필 동기화 완료 이벤트를 무시한다. eventId={}, applicantId={}",
                envelope.eventId(),
                data != null ? data.path("applicant_id").asText(null) : null);
    }
}

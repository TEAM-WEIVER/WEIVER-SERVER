package com.weiver.interview.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.interview.event.dto.InterviewReportCompletedData;
import com.weiver.interview.service.InterviewFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewReportCompletedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final InterviewFlowService interviewFlowService;

    @Override
    public EventType support() {
        return EventType.INTERVIEW_REPORT_COMPLETED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> envelope) {
        // AI 최종 평가 완료 이벤트를 서비스의 리포트 upsert 로직으로 위임한다.
        InterviewReportCompletedData data = objectMapper.convertValue(
                envelope.data(),
                InterviewReportCompletedData.class
        );
        interviewFlowService.handleReportCompleted(data);
    }
}

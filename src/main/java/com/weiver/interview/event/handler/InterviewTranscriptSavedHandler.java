package com.weiver.interview.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.interview.event.dto.InterviewTranscriptSavedData;
import com.weiver.interview.service.InterviewFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewTranscriptSavedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final InterviewFlowService interviewFlowService;

    @Override
    public EventType support() {
        return EventType.INTERVIEW_TRANSCRIPT_SAVED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> envelope) {
        // AI transcript 저장 완료 이벤트를 서비스의 후속 report 요청 로직으로 위임한다.
        InterviewTranscriptSavedData data = objectMapper.convertValue(
                envelope.data(),
                InterviewTranscriptSavedData.class
        );
        interviewFlowService.handleTranscriptSaved(data);
    }
}

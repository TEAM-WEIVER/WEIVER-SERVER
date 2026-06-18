package com.weiver.interview.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.interview.event.dto.InterviewQuestionGeneratedData;
import com.weiver.interview.service.InterviewFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class InterviewQuestionGeneratedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final InterviewFlowService interviewFlowService;

    @Override
    public EventType support() {
        return EventType.INTERVIEW_QUESTION_GENERATED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> envelope) {
        // AI 질문 생성 완료 이벤트를 서비스의 면접 상태 전이 로직으로 위임한다.
        InterviewQuestionGeneratedData data = objectMapper.convertValue(
                envelope.data(),
                InterviewQuestionGeneratedData.class
        );
        interviewFlowService.handleQuestionGenerated(data);
    }
}

package com.weiver.matching.event.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.event.consumer.DomainEventHandler;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.matching.event.dto.MatchingCompletedData;
import com.weiver.matching.service.MatchingResultReplacementService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashSet;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class MatchingCompletedHandler implements DomainEventHandler {

    private final ObjectMapper objectMapper;
    private final MatchingResultReplacementService replacementService;

    @Override
    public EventType support() {
        return EventType.MATCHING_COMPLETED;
    }

    @Override
    public void handle(EventEnvelope<JsonNode> envelope) {
        if (!StringUtils.hasText(envelope.eventId())) {
            throw new NonRetryableEventException("event_id is required");
        }
        MatchingCompletedData data = objectMapper.convertValue(
                envelope.data(),
                MatchingCompletedData.class
        );
        validate(data);

        replacementService.replace(envelope.eventId(), data);
    }

    private void validate(MatchingCompletedData data) {
        if (data.jdId() == null) {
            throw new NonRetryableEventException("jd_id is required");
        }
        if (data.matches() == null) {
            throw new NonRetryableEventException("matches is required");
        }

        Set<Long> applicantIds = new HashSet<>();
        for (MatchingCompletedData.MatchData match : data.matches()) {
            if (match.applicantId() == null) {
                throw new NonRetryableEventException("matches.applicant_id is required");
            }
            if (!applicantIds.add(match.applicantId())) {
                throw new NonRetryableEventException("Duplicate applicant_id in matches: " + match.applicantId());
            }
        }
    }
}

package com.weiver.global.event.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.exception.NonRetryableEventException;
import com.weiver.global.event.exception.UnsupportedEventVersionException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
public class EventEnvelopeValidator {

    private static final String SUPPORTED_VERSION = "1.0";

    public void validate(EventEnvelope<JsonNode> envelope) {
        if(envelope == null) {
            throw new NonRetryableEventException("Event envelope is null");
        }
        if (!StringUtils.hasText(envelope.eventId())) {
            throw new NonRetryableEventException("event_id is required");
        }
        if (envelope.eventType() == null) {
            throw new NonRetryableEventException("event_type is required");
        }
        if (!SUPPORTED_VERSION.equals(envelope.version())) {
            throw new UnsupportedEventVersionException(envelope.version());
        }
        if (envelope.occurredAt() == null) {
            throw new NonRetryableEventException("occurred_at is required");
        }
        if (envelope.data() == null || envelope.data().isNull()) {
            throw new NonRetryableEventException("data is required");
        }
    }
}

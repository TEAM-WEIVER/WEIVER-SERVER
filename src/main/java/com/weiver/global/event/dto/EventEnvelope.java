package com.weiver.global.event.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;

public record EventEnvelope<T>(
        @JsonProperty("event_id")
        String eventId,

        @JsonProperty("event_type")
        EventType eventType,

        @JsonProperty("correlation_id")
        String correlationId,

        @JsonProperty("occurred_at")
        OffsetDateTime occurredAt,

        String version,
        T data
) {
    public static final String CURRENT_VERSION = "1.0";

    public static <T> EventEnvelope<T> request(EventType eventType, T data, String eventId) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                null,
                OffsetDateTime.now(),
                CURRENT_VERSION,
                data
        );
    }

    public static <T> EventEnvelope<T> result(
            EventType eventType,
            T data,
            String eventId,
            String correlationId
    ) {
        return new EventEnvelope<>(
                eventId,
                eventType,
                correlationId,
                OffsetDateTime.now(),
                CURRENT_VERSION,
                data
        );
    }
}

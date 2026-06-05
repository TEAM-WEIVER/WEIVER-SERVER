package com.weiver.global.event.exception;

import com.weiver.global.event.dto.EventType;

public class UnsupportedEventTypeException extends NonRetryableEventException {
    public UnsupportedEventTypeException(EventType eventType) {
        super("Unsupported event type: " + eventType);
    }
}

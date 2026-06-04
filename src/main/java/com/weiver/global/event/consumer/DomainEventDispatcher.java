package com.weiver.global.event.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.exception.UnsupportedEventTypeException;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Component
public class DomainEventDispatcher {

    private final Map<EventType, DomainEventHandler> handlers;

    public DomainEventDispatcher(List<DomainEventHandler> handlerList) {
        this.handlers = new EnumMap<>(EventType.class);
        for(DomainEventHandler handler : handlerList) {
            this.handlers.put(handler.support(), handler);
        }
    }

    public void dispatch(EventEnvelope<JsonNode> envelope) {
        DomainEventHandler handler = handlers.get(envelope.eventType());
        if(handler == null) {
            throw new UnsupportedEventTypeException(envelope.eventType());
        }

        handler.handle(envelope);
    }
}

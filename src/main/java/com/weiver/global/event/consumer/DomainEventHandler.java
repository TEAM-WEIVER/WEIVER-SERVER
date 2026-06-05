package com.weiver.global.event.consumer;

import com.fasterxml.jackson.databind.JsonNode;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;

public interface DomainEventHandler {

    EventType support();

    void handle(EventEnvelope<JsonNode> envelope);
}

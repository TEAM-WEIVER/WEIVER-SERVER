package com.weiver.global.event.util;

import com.weiver.global.event.dto.EventType;

public final class EventRoutingKeys {
    private EventRoutingKeys() {}

    public static String from(EventType eventType) {
        return eventType.name().toLowerCase().replace("_", ".");
    }
}

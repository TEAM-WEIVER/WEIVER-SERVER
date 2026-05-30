package com.weiver.global.event.util;

import java.util.UUID;

public final class EventIds {

    private EventIds() {}

    public static String newEventId() {
        return UUID.randomUUID().toString();
    }
}

package com.weiver.global.logging.util;

import java.util.UUID;

public final class TraceIdGenerator {
    private TraceIdGenerator() {
    }

    public static String generate() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}

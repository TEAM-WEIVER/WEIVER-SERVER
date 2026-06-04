package com.weiver.global.event.exception;

public class UnsupportedEventVersionException extends NonRetryableEventException {
    public UnsupportedEventVersionException(String version) {
        super("Unsupported event version: " + version);
    }
}

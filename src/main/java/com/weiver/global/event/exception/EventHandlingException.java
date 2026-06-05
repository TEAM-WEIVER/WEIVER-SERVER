package com.weiver.global.event.exception;

public abstract class EventHandlingException extends RuntimeException {
    protected EventHandlingException(String message) {
        super(message);
    }

    protected EventHandlingException(String message, Throwable cause) {
        super(message, cause);
    }
}

package com.weiver.global.event.exception;

public class RetryableEventException extends EventHandlingException {
    public RetryableEventException(String message) {
        super(message);
    }

    public RetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

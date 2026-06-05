package com.weiver.global.event.exception;

public class NonRetryableEventException extends EventHandlingException{
    public NonRetryableEventException(String message) {
        super(message);
    }

    public NonRetryableEventException(String message, Throwable cause) {
        super(message, cause);
    }
}

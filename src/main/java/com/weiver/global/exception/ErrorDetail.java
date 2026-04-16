package com.weiver.global.exception;

public record ErrorDetail(
        String field,
        String message
) {
}

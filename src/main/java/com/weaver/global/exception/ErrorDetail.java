package com.weaver.global.exception;

public record ErrorDetail(
        String field,
        String message
) {
}

package com.weaver.global.exception;

import lombok.Getter;

@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode code;

    // 기본 메시지
    public BusinessException(ErrorCode code) {
        super(code.defaultMessage);
        this.code = code;
    }

    // 특수한 메시지
    public BusinessException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }
}

package com.weiver.global.exception;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import org.springframework.http.HttpStatusCode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ErrorResponse {

    private final String status;
    private final int httpStatus;
    private final String errorCode;
    private final String message;
    private final String timestamp;
    private final String path;
    private final List<ErrorDetail> errors;

    // 기본 메시지
    public static ErrorResponse of(ErrorCode errorCode, String path, List<ErrorDetail> errors) {
        return ErrorResponse.builder()
                .status("error")
                .httpStatus(errorCode.httpStatus.value())
                .errorCode(errorCode.code)
                .message(errorCode.defaultMessage)
                .timestamp(LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                )
                .path(path)
                .errors(errors)
                .build();
    }

    // 실제 HttpStatusCode를 사용하는 팩토리 (Spring MVC 예외의 실제 상태 코드 반영용)
    public static ErrorResponse of(HttpStatusCode httpStatus, ErrorCode errorCode, String path, List<ErrorDetail> errors) {
        return ErrorResponse.builder()
                .status("error")
                .httpStatus(httpStatus.value())
                .errorCode(errorCode.code)
                .message(errorCode.defaultMessage)
                .timestamp(LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                )
                .path(path)
                .errors(errors)
                .build();
    }

    // 커스텀 메시지
    public static ErrorResponse of(ErrorCode errorCode, String message, String path, List<ErrorDetail> errors) {
        return ErrorResponse.builder()
                .status("error")
                .httpStatus(errorCode.httpStatus.value())
                .errorCode(errorCode.code)
                .message(message)
                .timestamp(LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))
                )
                .path(path)
                .errors(errors)
                .build();
    }
}

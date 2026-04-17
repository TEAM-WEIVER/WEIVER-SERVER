package com.weiver.global.common;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {

    private final String status;    // "success", "fail"
    private final int code;         // HTTP code
    private final T data;
    private final String message;   // 사용자에게 보여줄 메시지

    /*
     * 데이터 + 메시지
     */
    public static <T> ApiResponse<T> success(int code, T data, String message) {
        return ApiResponse.<T>builder()
                .status("status")
                .code(code)
                .data(data)
                .message(message)
                .build();
    }

    /*
     * 데이터만 (조회)
     */
    public static <T> ApiResponse<T> success(T data) {
        return success(200, data, null);
    }

    /*
     * 메시지만 (삭제, 로그아웃 등)
     */
    public static ApiResponse<Void> success(String message) {
        return success(200, null, message);
    }

    /*
     * 201 Created
     */
    public static <T> ApiResponse<T> created(T data, String message) {
        return success(201, data, message);
    }

}

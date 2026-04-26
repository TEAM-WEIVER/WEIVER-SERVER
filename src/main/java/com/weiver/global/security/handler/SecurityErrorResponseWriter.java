package com.weiver.global.security.handler;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class SecurityErrorResponseWriter {

    private final ObjectMapper objectMapper;

    public void write(
            HttpServletResponse response,
            HttpServletRequest request,
            ErrorCode errorCode
    ) throws IOException {
        response.setStatus(errorCode.httpStatus.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ErrorResponse errorResponse = ErrorResponse.of(
                errorCode,
                request.getRequestURI(),
                List.of()
        );

        objectMapper.writeValue(response.getWriter(), errorResponse);
    }
}

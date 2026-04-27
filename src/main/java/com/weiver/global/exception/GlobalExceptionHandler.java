package com.weiver.global.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestCookieException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    // ===================== 우리가 만든 예외 =====================

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusiness(
            BusinessException ex,
            HttpServletRequest request) {

        log.warn("[BusinessException] errorCode={}, message={}, path={}",
                ex.getCode().code, ex.getMessage(), request.getRequestURI());

        return ResponseEntity
                .status(ex.getCode().httpStatus)
                .body(ErrorResponse.of(
                        ex.getCode(),
                        ex.getMessage(),
                        request.getRequestURI(),
                        List.of()
                ));
    }

    // ===================== Spring 예외 — @ExceptionHandler =====================

    @ExceptionHandler(MissingRequestCookieException.class)
    public ResponseEntity<ErrorResponse> handleMissingCookie(
            MissingRequestCookieException ex,
            HttpServletRequest request) {

        return toResponse(ErrorCode.MISSING_COOKIE, request, List.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, ConstraintViolationException.class})
    public ResponseEntity<ErrorResponse> handlePathAndConstraint(
            Exception ex,
            HttpServletRequest request) {

        List<ErrorDetail> details = extractDetails(ex);
        return toResponse(ErrorCode.BIND_FAILED, request, details);
    }

    // ===================== Spring 예외 — override =====================

    @Override
    protected ResponseEntity<Object> handleNoResourceFoundException(
            NoResourceFoundException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        log.warn("[NoResourceFound] path={}", ex.getMessage());
        return toResponseObject(ErrorCode.BAD_REQUEST, getPath(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ErrorDetail> details = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ErrorDetail(fe.getField(), fe.getDefaultMessage()))
                .toList();

        return toResponseObject(ErrorCode.VALIDATION_FAILED, getPath(request), details);
    }

    @Override
    protected ResponseEntity<Object> handleMissingServletRequestParameter(
            MissingServletRequestParameterException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        List<ErrorDetail> details = List.of(
                new ErrorDetail(ex.getParameterName(), "필수 요청 파라미터가 누락되었습니다.")
        );
        return toResponseObject(ErrorCode.BIND_FAILED, getPath(request), details);
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(
                        ErrorCode.MALFORMED_JSON,
                        getPath(request),
                        List.of()
                ));
    }

    @Override
    protected ResponseEntity<Object> handleHttpRequestMethodNotSupported(
            HttpRequestMethodNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return toResponseObject(ErrorCode.METHOD_NOT_ALLOWED, getPath(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMediaTypeNotSupported(
            HttpMediaTypeNotSupportedException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {

        return toResponseObject(ErrorCode.UNSUPPORTED_MEDIA_TYPE, getPath(request), List.of());
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception ex,
            Object body,
            HttpHeaders headers,
            HttpStatusCode statusCode,
            WebRequest request) {

        log.error("[UnhandledException] message={}", ex.getMessage(), ex);
        return toResponseObject(ErrorCode.INTERNAL_SERVER_ERROR, getPath(request), List.of());
    }

    // ===================== private 헬퍼 =====================

    private ResponseEntity<ErrorResponse> toResponse(
            ErrorCode errorCode,
            HttpServletRequest request,
            List<ErrorDetail> details) {

        return ResponseEntity
                .status(errorCode.httpStatus)
                .body(ErrorResponse.of(errorCode, request.getRequestURI(), details));
    }

    private ResponseEntity<Object> toResponseObject(
            ErrorCode errorCode,
            String path,
            List<ErrorDetail> details) {

        return ResponseEntity
                .status(errorCode.httpStatus)
                .body(ErrorResponse.of(errorCode, path, details));
    }

    private String getPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    private List<ErrorDetail> extractDetails(Exception ex) {
        if (ex instanceof MethodArgumentTypeMismatchException e) {
            String requiredType = e.getRequiredType() != null
                    ? e.getRequiredType().getSimpleName()
                    : "알 수 없음";
            return List.of(new ErrorDetail(
                    e.getName(),
                    "'" + e.getValue() + "' 값은 '" + requiredType + "' 타입으로 변환할 수 없습니다."
            ));
        }
        if (ex instanceof ConstraintViolationException e) {
            return e.getConstraintViolations().stream()
                    .map(v -> new ErrorDetail(
                            v.getPropertyPath().toString(),
                            v.getMessage()))
                    .toList();
        }
        return List.of();
    }

}
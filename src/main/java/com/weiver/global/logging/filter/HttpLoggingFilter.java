package com.weiver.global.logging.filter;

import com.weiver.global.logging.util.TraceIdGenerator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class HttpLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        long startTime = System.currentTimeMillis();

        String traceId = resolveTraceId(request);
        MDC.put(TRACE_ID, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);

        try {
            logRequest(request);
            filterChain.doFilter(request,response);
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            logResponse(request, response, duration);
            MDC.clear();
        }
    }

    private void logRequest(HttpServletRequest request) {
        log.info(
                "[HTTP REQUEST] method={} uri={} query={} clientIp={} userAgent={}",
                request.getMethod(),
                request.getRequestURI(),
                request.getQueryString(),
                getClientIp(request),
                request.getHeader("User-Agent")
        );
    }

    private void logResponse(HttpServletRequest request, HttpServletResponse response, long duration) {
        int status = response.getStatus();

        if(status >= 500) {
            log.error(
                    "[HTTP RESPONSE] method={} uri={} status={} duration={}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    duration
            );
            return;
        }

        if(status >= 400) {
            log.warn(
                    "[HTTP RESPONSE] method={} uri={} status={} duration={}ms",
                    request.getMethod(),
                    request.getRequestURI(),
                    status,
                    duration
            );
            return;
        }

        log.info(
                "[HTTP RESPONSE] method={} uri={} status={} duration={}ms",
                request.getMethod(),
                request.getRequestURI(),
                status,
                duration
        );
    }

    private String resolveTraceId(HttpServletRequest request) {
        String traceId = request.getHeader(TRACE_ID_HEADER);

        if(traceId == null || traceId.isBlank()) {
            return TraceIdGenerator.generate();
        }

        return traceId;
    }

    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");

        if(xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }
}

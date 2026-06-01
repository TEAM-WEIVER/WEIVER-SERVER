package com.weiver.global.logging.sentry.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Tag(name = "Monitoring", description = "Sentry 연동 테스트용 API")
@RestController
@RequestMapping("/api/test")
public class SentryTestController {

    @Operation(
            summary = "Sentry 연동 테스트용 API",
            description = "강제로 500 RuntimeException을 발생시켜 Sentry 실시간 에러 수집 여부를 검증합니다."
    )
    @GetMapping("/sentry-error")
    public void throwSentryTestException() {
        throw new RuntimeException("Weiver 프로젝트 Sentry 실시간 연동 테스트 에러 (500 Error)");
    }
}

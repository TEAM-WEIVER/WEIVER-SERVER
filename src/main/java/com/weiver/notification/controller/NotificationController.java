package com.weiver.notification.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.notification.dto.response.NotificationResponseDTO;
import com.weiver.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "알림 API", description = "기업/구직자 알림 관리 API")
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {
    private final NotificationService notificationService;

    @Operation(summary = "알림 목록 조회", description = "현재 로그인한 사용자의 알림 목록을 최신순으로 조회합니다.")
    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, List<NotificationResponseDTO>>>> getNotifications(
            @AuthenticationPrincipal AuthenticatedPrincipal principal) {

        List<NotificationResponseDTO> notifications = notificationService.getCompanyNotifications(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(Map.of("NotificationDTO", notifications)));
    }
}

package com.weiver.notification.controller;

import com.weiver.global.common.ApiResponse;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.security.principal.AuthenticatedPrincipal;
import com.weiver.notification.dto.response.NotificationResponseDTO;
import com.weiver.notification.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

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
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        List<NotificationResponseDTO> notifications = notificationService.getCompanyNotifications(principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(Map.of("NotificationDTO", notifications)));
    }

    @Operation(summary = "알림 읽음 처리", description = "해당 공고 매칭 지원자 리스트 페이지로 이동 함과 동시에" +
            " 해당 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<Void>> readNotification(
            @Parameter(description = "알림 고유 ID") @PathVariable Long notificationId,
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        if (principal == null) throw new BusinessException(ErrorCode.UNAUTHORIZED);

        notificationService.markAsRead(notificationId, principal.publicId());

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @Operation(summary = "알림 전체 읽음 처리", description = "현재 사용자의 모든 미읽음 알림을 읽음 상태로 변경합니다.")
    @PatchMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> readAllNotifications(
            @AuthenticationPrincipal @Parameter(hidden = true) AuthenticatedPrincipal principal) {

        notificationService.markAllAsRead(principal.publicId());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}

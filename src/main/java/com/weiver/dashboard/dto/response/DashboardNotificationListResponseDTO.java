package com.weiver.dashboard.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weiver.notification.dto.response.NotificationResponseDTO;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "기업 대시보드 알림 목록 응답 DTO")
public record DashboardNotificationListResponseDTO(
        @Schema(description = "알림 목록")
        @JsonProperty("NotificationDTO")
        List<NotificationResponseDTO> notifications
) {
    public static DashboardNotificationListResponseDTO from(List<NotificationResponseDTO> notifications) {
        return new DashboardNotificationListResponseDTO(notifications);
    }
}

package com.weiver.notification.dto.response;

import com.weiver.notification.domain.Notification;

import java.time.LocalDateTime;

public record NotificationResponseDTO(
        Long notificationId,
        String type,
        String message,
        Boolean isRead,
        Long jdId,
        LocalDateTime createdAt
) {
    public static NotificationResponseDTO from(Notification notification) {
        return new NotificationResponseDTO(
                notification.getNotificationId(),
                notification.getType().getDescription(),
                notification.getMessage(),
                notification.getIsRead(),
                notification.getMatchResult().getJobPosting().getJdId(),
                notification.getCreateTime()
        );
    }
}

package com.weiver.notification.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.notification.domain.Notification;
import com.weiver.notification.dto.response.NotificationResponseDTO;
import com.weiver.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public List<NotificationResponseDTO> getCompanyNotifications(String companyPublicId) {

        return notificationRepository.findAllByCompany_PublicIdOrderByCreateTimeDesc(companyPublicId)
                .stream()
                .map(NotificationResponseDTO::from)
                .toList();
    }

    @Transactional
    public void markAsRead(Long notificationId, String companyPublicId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND));

        if (!notification.getCompany().getPublicId().equals(companyPublicId)) {
            throw new BusinessException(ErrorCode.NOTIFICATION_FORBIDDEN);
        }

        notification.markAsRead();
    }

    @Transactional
    public void markAllAsRead(String companyPublicId) {
        List<Notification> unreadNotifications = notificationRepository
                .findAllByCompany_PublicIdAndIsReadFalse(companyPublicId);

        unreadNotifications.forEach(Notification::markAsRead);
    }
}

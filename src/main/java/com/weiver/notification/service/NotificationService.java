package com.weiver.notification.service;

import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.notification.domain.Notification;
import com.weiver.notification.dto.response.NotificationResponseDTO;
import com.weiver.notification.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private static final int MAX_NOTIFICATION_PAGE_SIZE = 100;

    public Slice<NotificationResponseDTO> getCompanyNotifications(String companyPublicId, int page, int size) {

        validatePageRequest(page, size);

        Pageable pageable = PageRequest.of(page, size);

        return notificationRepository
                .findSliceByCompanyPublicId(companyPublicId, pageable)
                .map(NotificationResponseDTO::from);
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

    private void validatePageRequest(int page, int size) {
        if (page < 0 || size < 1 || size > MAX_NOTIFICATION_PAGE_SIZE) {
            throw new BusinessException(
                    ErrorCode.BAD_REQUEST,
                    "page는 0 이상, size는 1 이상 100 이하여야 합니다."
            );
        }
    }
}

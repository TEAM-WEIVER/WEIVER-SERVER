package com.weiver.notification.service;

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

    /**
     * 알림 조회
     * */
    public List<NotificationResponseDTO> getCompanyNotifications(String companyPublicId) {

        return notificationRepository.findAllByCompany_PublicIdOrderByCreateTimeDesc(companyPublicId)
                .stream()
                .map(NotificationResponseDTO::from)
                .toList();
    }
}

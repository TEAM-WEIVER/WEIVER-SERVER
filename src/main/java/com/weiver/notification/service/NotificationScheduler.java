package com.weiver.notification.service;

import com.weiver.company.repository.CompanyRepository;
import com.weiver.matching.dto.response.MatchingNotificationGroupDTO;
import com.weiver.matching.repository.MatchResultRepository;
import com.weiver.matching.repository.MatchingProcessingLockRepository;
import com.weiver.notification.domain.Notification;
import com.weiver.notification.repository.NotificationRepository;
import com.weiver.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationScheduler {

    private final MatchResultRepository matchResultRepository;
    private final NotificationRepository notificationRepository;
    private final CompanyRepository companyRepository;
    private final MatchingProcessingLockRepository processingLockRepository;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    @Scheduled(cron = "0 0 * * * *")
    public void generateGroupedMatchingNotifications() {
        log.info("[Notification Batch] 새로운 매칭 알림 생성 배치 시작");

        if (!processingLockRepository.tryLock()) {
            log.info("[Notification Batch] 다른 매칭 처리가 실행 중이므로 이번 실행을 건너뜁니다.");
            return;
        }

        List<MatchingNotificationGroupDTO> groups = matchResultRepository.findUnnotifiedGroups();

        if (groups.isEmpty()) {
            log.info("[Notification Batch] 생성할 알림이 없습니다. 배치 종료.");
            return;
        }

        List<Notification> newNotifications = groups.stream()
                .map(group -> Notification.builder()
                        .company(companyRepository.getReferenceById(group.companyId()))
                        .matchId(group.representativeMatchId())
                        .jdId(group.jdId())
                        .type(NotificationType.RESUME_MATCH_TALENT)
                        .message(String.format("[%s] 공고에 %d건의 새로운 매칭이 있습니다.",
                                group.title(), group.matchCount()))
                        .build())
                .toList();

        notificationRepository.saveAll(newNotifications);
        matchResultRepository.markAllUnnotifiedAsNotified();

        log.info("[Notification Batch] {}건의 새로운 그룹 알림이 생성되었습니다.", newNotifications.size());
    }
}

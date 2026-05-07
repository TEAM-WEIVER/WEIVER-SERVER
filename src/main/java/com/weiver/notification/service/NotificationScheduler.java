package com.weiver.notification.service;

import com.weiver.jobposting.domain.JobPosting;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.repository.MatchResultRepository;
import com.weiver.notification.domain.Notification;
import com.weiver.notification.repository.NotificationRepository;
import com.weiver.notification.type.NotificationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationScheduler {

    private final MatchResultRepository matchResultRepository;
    private final NotificationRepository notificationRepository;

    @Transactional
    @Scheduled(cron = "0 0 * * * *")
    public void generateGroupedMatchingNotifications() {
        log.info("[Notification Batch] 새로운 매칭 알림 생성 배치 시작");

        List<MatchResult> unnotifiedMatches = matchResultRepository.findAllByIsNotifiedFalse();

        if (unnotifiedMatches.isEmpty()) {
            log.info("[Notification Batch] 생성할 알림이 없습니다. 배치 종료.");
            return;
        }

        Map<JobPosting, List<MatchResult>> matchesByJobPosting = unnotifiedMatches.stream()
                .collect(Collectors.groupingBy(MatchResult::getJobPosting));

        List<Notification> newNotifications = new ArrayList<>();

        for (Map.Entry<JobPosting, List<MatchResult>> entry : matchesByJobPosting.entrySet()) {
            JobPosting jobPosting = entry.getKey();
            List<MatchResult> groupMatches = entry.getValue();

            int matchCount = groupMatches.size();

            MatchResult representativeMatch = groupMatches.stream()
                    .max(Comparator.comparing(MatchResult::getCreateTime))
                    .orElse(groupMatches.get(0));

            String message = String.format("[%s] 공고에 %d건의 새로운 매칭이 있습니다.", jobPosting.getTitle(), matchCount);

            Notification notification = Notification.builder()
                    .company(jobPosting.getCompany())
                    .matchResult(representativeMatch)
                    .type(NotificationType.RESUME_MATCH_TALENT)
                    .message(message)
                    .build();

            newNotifications.add(notification);

            groupMatches.forEach(MatchResult::markAsNotified);
        }

        notificationRepository.saveAll(newNotifications);

        log.info("[Notification Batch] {}건의 새로운 그룹 알림이 생성되었습니다.", newNotifications.size());
    }
}

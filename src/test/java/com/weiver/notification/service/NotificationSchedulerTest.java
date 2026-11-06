package com.weiver.notification.service;

import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.matching.dto.response.MatchingNotificationGroupDTO;
import com.weiver.matching.repository.MatchResultRepository;
import com.weiver.matching.repository.MatchingProcessingLockRepository;
import com.weiver.notification.domain.Notification;
import com.weiver.notification.repository.NotificationRepository;
import com.weiver.notification.type.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @InjectMocks private NotificationScheduler notificationScheduler;
    @Mock private MatchResultRepository matchResultRepository;
    @Mock private NotificationRepository notificationRepository;
    @Mock private CompanyRepository companyRepository;
    @Mock private MatchingProcessingLockRepository processingLockRepository;

    @Test
    @DisplayName("다른 처리가 잠금을 보유하면 집계와 갱신을 건너뛴다")
    void generateGroupedMatchingNotifications_SkipsWhenLocked() {
        // given
        given(processingLockRepository.tryLock()).willReturn(false);

        // when
        notificationScheduler.generateGroupedMatchingNotifications();

        // then
        verifyNoInteractions(matchResultRepository, notificationRepository, companyRepository);
    }

    @Test
    @DisplayName("미알림 그룹이 없으면 알림 저장과 상태 갱신을 실행하지 않는다")
    void generateGroupedMatchingNotifications_EmptyGroups_ReturnsEarly() {
        // given
        given(processingLockRepository.tryLock()).willReturn(true);
        given(matchResultRepository.findUnnotifiedGroups()).willReturn(List.of());

        // when
        notificationScheduler.generateGroupedMatchingNotifications();

        // then
        verify(notificationRepository, never()).saveAll(anyList());
        verify(matchResultRepository, never()).markAllUnnotifiedAsNotified();
        verifyNoInteractions(companyRepository);
    }

    @Test
    @DisplayName("공고별 집계로 알림을 생성하고 저장 후 한 번에 알림 상태를 갱신한다")
    void generateGroupedMatchingNotifications_SavesGroupsThenBulkUpdates() {
        // given
        Company company = Company.builder().companyId(10L).build();
        LocalDateTime latest = LocalDateTime.of(2026, 11, 6, 12, 0);
        given(processingLockRepository.tryLock()).willReturn(true);
        given(matchResultRepository.findUnnotifiedGroups()).willReturn(List.of(
                new MatchingNotificationGroupDTO(1L, "백엔드 개발자", 10L, 3L, latest, 3L),
                new MatchingNotificationGroupDTO(2L, "프론트엔드 개발자", 10L, 1L, latest, 4L)));
        given(companyRepository.getReferenceById(10L)).willReturn(company);

        // when
        notificationScheduler.generateGroupedMatchingNotifications();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        var order = inOrder(processingLockRepository, matchResultRepository, notificationRepository);
        order.verify(processingLockRepository).tryLock();
        order.verify(matchResultRepository).findUnnotifiedGroups();
        order.verify(notificationRepository).saveAll(captor.capture());
        order.verify(matchResultRepository).markAllUnnotifiedAsNotified();
        assertThat(captor.getValue()).hasSize(2);
        assertThat(captor.getValue()).extracting(Notification::getMessage).containsExactly(
                "[백엔드 개발자] 공고에 3건의 새로운 매칭이 있습니다.",
                "[프론트엔드 개발자] 공고에 1건의 새로운 매칭이 있습니다.");
        assertThat(captor.getValue()).extracting(Notification::getMatchId).containsExactly(3L, 4L);
        assertThat(captor.getValue()).extracting(Notification::getJdId).containsExactly(1L, 2L);
        assertThat(captor.getValue()).allSatisfy(notification -> {
            assertThat(notification.getCompany()).isSameAs(company);
            assertThat(notification.getType()).isEqualTo(NotificationType.RESUME_MATCH_TALENT);
        });
    }
}
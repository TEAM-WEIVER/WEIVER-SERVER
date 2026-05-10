package com.weiver.notification.service;

import com.weiver.company.domain.Company;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.repository.MatchResultRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSchedulerTest {

    @InjectMocks
    private NotificationScheduler notificationScheduler;

    @Mock
    private MatchResultRepository matchResultRepository;

    @Mock
    private NotificationRepository notificationRepository;

    @Test
    @DisplayName("엣지케이스: 알림을 생성할 매칭 결과가 없으면 로직이 바로 종료된다.")
    void generateGroupedMatchingNotifications_EmptyList_ReturnsEarly() {
        // given
        given(matchResultRepository.findAllByIsNotifiedFalse()).willReturn(Collections.emptyList());

        // when
        notificationScheduler.generateGroupedMatchingNotifications();

        // then
        verify(notificationRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("성공: 다수의 매칭이 공고별로 정확히 그룹핑되며, 최신 매칭이 대표로 지정된다.")
    void generateGroupedMatchingNotifications_Success_GroupsCorrectly() {
        // given
        Company company = Company.builder().publicId("comp-1").build();

        // 공고 2개 준비
        JobPosting backendJd = JobPosting.builder().title("백엔드 개발자").company(company).build();
        JobPosting frontendJd = JobPosting.builder().title("프론트엔드 개발자").company(company).build();

        // 백엔드 공고에 대한 매칭 3건 (시간을 다르게 세팅)
        MatchResult backendMatch1 = MatchResult.builder().jobPosting(backendJd).build();
        MatchResult backendMatch2 = MatchResult.builder().jobPosting(backendJd).build();
        MatchResult backendMatch3 = MatchResult.builder().jobPosting(backendJd).build();

        ReflectionTestUtils.setField(backendMatch1, "createTime", LocalDateTime.now().minusHours(2));
        ReflectionTestUtils.setField(backendMatch2, "createTime", LocalDateTime.now().minusHours(1));
        ReflectionTestUtils.setField(backendMatch3, "createTime", LocalDateTime.now()); // 가장 최신 데이터

        // 프론트엔드 공고에 대한 매칭 1건
        MatchResult frontendMatch1 = MatchResult.builder().jobPosting(frontendJd).build();
        ReflectionTestUtils.setField(frontendMatch1, "createTime", LocalDateTime.now());

        // 아직 알림 안 간 매칭 4건 반환
        given(matchResultRepository.findAllByIsNotifiedFalse())
                .willReturn(List.of(backendMatch1, backendMatch2, backendMatch3, frontendMatch1));

        // when
        notificationScheduler.generateGroupedMatchingNotifications();

        // then
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        verify(notificationRepository).saveAll(captor.capture());
        List<Notification> savedNotifications = captor.getValue();

        assertThat(savedNotifications).hasSize(2);

        Notification backendNotification = savedNotifications.stream()
                .filter(n -> n.getMessage().contains("백엔드"))
                .findFirst().orElseThrow();

        assertThat(backendNotification.getMessage()).isEqualTo("[백엔드 개발자] 공고에 3건의 새로운 매칭이 있습니다.");
        assertThat(backendNotification.getType()).isEqualTo(NotificationType.RESUME_MATCH_TALENT);
        assertThat(backendNotification.getMatchResult()).isEqualTo(backendMatch3);

        Notification frontendNotification = savedNotifications.stream()
                .filter(n -> n.getMessage().contains("프론트엔드"))
                .findFirst().orElseThrow();

        assertThat(frontendNotification.getMessage()).isEqualTo("[프론트엔드 개발자] 공고에 1건의 새로운 매칭이 있습니다.");
        assertThat(frontendNotification.getMatchResult()).isEqualTo(frontendMatch1);

        // 상태 변경 검증
        assertThat(backendMatch1.getIsNotified()).isTrue();
        assertThat(backendMatch2.getIsNotified()).isTrue();
        assertThat(backendMatch3.getIsNotified()).isTrue();
        assertThat(frontendMatch1.getIsNotified()).isTrue();
    }
}
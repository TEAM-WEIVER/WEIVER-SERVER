package com.weiver.notification.service;

import com.weiver.company.domain.Company;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.matching.domain.MatchResult;
import com.weiver.notification.domain.Notification;
import com.weiver.notification.dto.response.NotificationResponseDTO;
import com.weiver.notification.repository.NotificationRepository;
import com.weiver.notification.type.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @InjectMocks
    private NotificationService notificationService;

    @Mock
    private NotificationRepository notificationRepository;


    private Notification createMockNotification(Long notificationId, String companyPublicId, Long jdId, boolean isRead) {
        Company company = Company.builder()
                .publicId(companyPublicId)
                .build();
        ReflectionTestUtils.setField(company, "companyId", 1L);

        JobPosting jobPosting = JobPosting.builder()
                .company(company)
                .title("테스트 공고")
                .build();
        ReflectionTestUtils.setField(jobPosting, "jdId", jdId);

        MatchResult matchResult = MatchResult.builder()
                .jobPosting(jobPosting)
                .build();
        ReflectionTestUtils.setField(matchResult, "matchId", 1L);

        Notification notification = Notification.builder()
                .company(company)
                .matchResult(matchResult)
                .type(NotificationType.RESUME_MATCH_TALENT)
                .message("테스트 알림입니다.")
                .build();

        ReflectionTestUtils.setField(notification, "notificationId", notificationId);
        ReflectionTestUtils.setField(notification, "createTime", LocalDateTime.now());

        if (isRead) {
            notification.markAsRead();
        }

        return notification;
    }

    @Test
    @DisplayName("성공: 알림 목록이 NPE 없이 정상적으로 DTO로 변환되어 반환된다.")
    void getCompanyNotifications_Success() {
        // given
        String companyPublicId = "comp-123";
        Notification noti1 = createMockNotification(1L, companyPublicId, 100L, false);
        Notification noti2 = createMockNotification(2L, companyPublicId, 200L, true);

        given(notificationRepository.findAllByCompany_PublicIdOrderByCreateTimeDesc(companyPublicId))
                .willReturn(List.of(noti1, noti2));

        // when
        List<NotificationResponseDTO> result = notificationService.getCompanyNotifications(companyPublicId);

        // then
        assertThat(result).hasSize(2);

        assertThat(result.get(0).notificationId()).isEqualTo(1L);
        assertThat(result.get(0).jdId()).isEqualTo(100L);
        assertThat(result.get(0).isRead()).isFalse();

        assertThat(result.get(1).notificationId()).isEqualTo(2L);
        assertThat(result.get(1).jdId()).isEqualTo(200L);
        assertThat(result.get(1).isRead()).isTrue();
    }

    @Test
    @DisplayName("성공 엣지케이스: 알림이 하나도 없는 경우 빈 리스트를 반환한다.")
    void getCompanyNotifications_ReturnsEmptyList() {
        // given
        given(notificationRepository.findAllByCompany_PublicIdOrderByCreateTimeDesc(anyString()))
                .willReturn(Collections.emptyList());

        // when
        List<NotificationResponseDTO> result = notificationService.getCompanyNotifications("comp-123");

        // then
        assertThat(result).isEmpty();
    }


    @Test
    @DisplayName("성공: 권한이 일치하면 단일 알림이 정상적으로 읽음 처리(상태 변경)된다.")
    void markAsRead_Success() {
        // given
        Long notificationId = 1L;
        String companyPublicId = "comp-123";
        Notification notification = createMockNotification(notificationId, companyPublicId, 100L, false);

        given(notificationRepository.findById(notificationId))
                .willReturn(Optional.of(notification));

        // when
        notificationService.markAsRead(notificationId, companyPublicId);

        // then
        assertThat(notification.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("실패 엣지케이스: 존재하지 않는 알림 ID를 요청하면 NOTIFICATION_NOT_FOUND 예외가 발생한다.")
    void markAsRead_ThrowsNotFound() {
        // given
        Long invalidId = 999L;
        given(notificationRepository.findById(invalidId)).willReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(invalidId, "comp-123"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTIFICATION_NOT_FOUND.defaultMessage);
    }

    @Test
    @DisplayName("실패 엣지케이스: 알림의 소유자(Company)와 요청자가 다르면 FORBIDDEN 예외가 발생한다.")
    void markAsRead_ThrowsForbidden() {
        // given
        Long notificationId = 1L;
        String ownerPublicId = "comp-owner";
        String hackerPublicId = "comp-hacker";

        Notification notification = createMockNotification(notificationId, ownerPublicId, 100L, false);

        given(notificationRepository.findById(notificationId))
                .willReturn(Optional.of(notification));

        // when & then
        assertThatThrownBy(() -> notificationService.markAsRead(notificationId, hackerPublicId))
                .isInstanceOf(BusinessException.class)
                .hasMessage(ErrorCode.NOTIFICATION_FORBIDDEN.defaultMessage);
    }

    @Test
    @DisplayName("성공: 기업의 읽지 않은 모든 알림들이 일괄적으로 읽음 처리된다.")
    void markAllAsRead_Success() {
        // given
        String companyPublicId = "comp-123";
        Notification unread1 = createMockNotification(1L, companyPublicId, 100L, false);
        Notification unread2 = createMockNotification(2L, companyPublicId, 200L, false);

        given(notificationRepository.findAllByCompany_PublicIdAndIsReadFalse(companyPublicId))
                .willReturn(List.of(unread1, unread2));

        // when
        notificationService.markAllAsRead(companyPublicId);

        // then
        assertThat(unread1.getIsRead()).isTrue();
        assertThat(unread2.getIsRead()).isTrue();
    }

    @Test
    @DisplayName("성공 엣지케이스: 읽지 않은 알림이 없어도 로직이 안전하게 통과된다.")
    void markAllAsRead_Success_WhenNoUnread() {
        // given
        String companyPublicId = "comp-123";
        given(notificationRepository.findAllByCompany_PublicIdAndIsReadFalse(companyPublicId))
                .willReturn(Collections.emptyList());

        // when
        notificationService.markAllAsRead(companyPublicId);

        // then
        verify(notificationRepository).findAllByCompany_PublicIdAndIsReadFalse(companyPublicId);
    }
}
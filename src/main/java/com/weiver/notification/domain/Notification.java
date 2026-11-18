package com.weiver.notification.domain;

import com.weiver.company.domain.Company;
import com.weiver.global.common.BaseTimeEntity;
import com.weiver.notification.type.NotificationType;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(
        name = "notifications",
        indexes = @Index(
                name = "idx_notifications_company_created_id",
                columnList = "company_id, create_time DESC, notification_id DESC"
        )
)
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private NotificationType type;

    @Column(name = "message", nullable = false)
    private String message;

    @Builder.Default
    @Column(name = "is_read", nullable = false)
    private Boolean isRead = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    // 알림 생성 당시의 식별자 스냅샷. 매칭 결과 교체 후에도 알림을 보존한다.
    @Column(name = "match_id", nullable = false)
    private Long matchId;

    @Column(name = "jd_id", nullable = false)
    private Long jdId;

    public void markAsRead() {
        this.isRead = true;
    }
}

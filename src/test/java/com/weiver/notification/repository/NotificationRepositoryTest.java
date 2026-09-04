package com.weiver.notification.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.applicant.domain.Applicant;
import com.weiver.company.domain.Company;
import com.weiver.company.type.CompanyType;
import com.weiver.company.type.DecisionMaking;
import com.weiver.company.type.OperationStyle;
import com.weiver.company.type.RoleDefinition;
import com.weiver.company.type.WorkPace;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.matching.domain.MatchResult;
import com.weiver.notification.domain.Notification;
import com.weiver.notification.type.NotificationType;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(NotificationRepositoryTest.JpaAuditingTestConfig.class)
class NotificationRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class JpaAuditingTestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres =
            new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("해당 기업의 알림만 최신순·ID 내림차순으로 Slice 조회한다.")
    void findSliceByCompanyPublicId_ReturnsOnlyCompanyNotificationsInStableOrder() {
        // given
        Company targetCompany = persistCompany("target-company", "target-login");
        Company otherCompany = persistCompany("other-company", "other-login");

        MatchResult targetMatchResult = persistMatchResult(targetCompany, "target");
        MatchResult otherMatchResult = persistMatchResult(otherCompany, "other");

        Notification oldest = persistNotification(targetCompany, targetMatchResult, "oldest");
        Notification sameTimeLowerId = persistNotification(targetCompany, targetMatchResult, "same-lower");
        Notification sameTimeHigherId = persistNotification(targetCompany, targetMatchResult, "same-higher");
        Notification otherCompanyNotification =
                persistNotification(otherCompany, otherMatchResult, "other-company");

        entityManager.flush();

        LocalDateTime sameTime = LocalDateTime.of(2026, 1, 2, 12, 0);
        updateCreateTime(oldest.getNotificationId(), LocalDateTime.of(2026, 1, 1, 12, 0));
        updateCreateTime(sameTimeLowerId.getNotificationId(), sameTime);
        updateCreateTime(sameTimeHigherId.getNotificationId(), sameTime);
        updateCreateTime(otherCompanyNotification.getNotificationId(), LocalDateTime.of(2026, 1, 3, 12, 0));
        entityManager.clear();

        // when
        Slice<Notification> firstSlice = notificationRepository.findSliceByCompanyPublicId(
                targetCompany.getPublicId(),
                PageRequest.of(0, 2)
        );

        // then
        assertThat(firstSlice.getContent())
                .extracting(Notification::getNotificationId)
                .containsExactly(
                        sameTimeHigherId.getNotificationId(),
                        sameTimeLowerId.getNotificationId()
                );
        assertThat(firstSlice.hasNext()).isTrue();

        PersistenceUnitUtil persistenceUnitUtil =
                entityManager.getEntityManagerFactory().getPersistenceUnitUtil();
        assertThat(firstSlice.getContent())
                .allSatisfy(notification -> {
                    assertThat(persistenceUnitUtil.isLoaded(notification, "matchResult")).isTrue();
                    assertThat(persistenceUnitUtil.isLoaded(
                            notification.getMatchResult(),
                            "jobPosting"
                    )).isTrue();
                });

        Slice<Notification> secondSlice = notificationRepository.findSliceByCompanyPublicId(
                targetCompany.getPublicId(),
                PageRequest.of(1, 2)
        );

        assertThat(secondSlice.getContent())
                .extracting(Notification::getNotificationId)
                .containsExactly(oldest.getNotificationId());
        assertThat(secondSlice.hasNext()).isFalse();
        assertThat(secondSlice.isLast()).isTrue();
    }

    @Test
    @DisplayName("존재하지 않는 기업 publicId로 조회하면 빈 Slice를 반환한다.")
    void findSliceByCompanyPublicId_ReturnsEmptySlice_WhenCompanyDoesNotExist() {
        Slice<Notification> result = notificationRepository.findSliceByCompanyPublicId(
                "missing-company",
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent()).isEmpty();
        assertThat(result.hasNext()).isFalse();
        assertThat(result.isLast()).isTrue();
    }

    private Company persistCompany(String publicId, String loginId) {
        Company company = Company.builder()
                .publicId(publicId)
                .loginId(loginId)
                .password("encoded-password")
                .companyType(CompanyType.STARTUP)
                .employeeNum(10)
                .companyCeoName("홍길동")
                .companyName(loginId + " company")
                .foundedYear(LocalDate.of(2020, 1, 1))
                .avgSale(100)
                .address("서울시")
                .cultureDescription("수평적인 문화")
                .directionDescription("성장")
                .workPace(WorkPace.FAST_EXECUTION)
                .decisionMaking(DecisionMaking.TEAM_CONSENSUS)
                .roleDefinition(RoleDefinition.CLEAR_RESPONSIBILITY)
                .operationStyle(OperationStyle.STABILITY_ORIENTED)
                .build();
        entityManager.persist(company);
        return company;
    }

    private MatchResult persistMatchResult(Company company, String suffix) {
        JobPosting jobPosting = JobPosting.builder()
                .company(company)
                .title(suffix + " job")
                .jobCategory("IT")
                .detailedJob("backend")
                .deadline(LocalDate.now().plusDays(30))
                .status(JobPostingStatus.ACTIVE)
                .build();
        entityManager.persist(jobPosting);

        Applicant applicant = Applicant.builder()
                .publicId(suffix + "-applicant")
                .email(suffix + "@test.com")
                .password("encoded-password")
                .build();
        entityManager.persist(applicant);

        MatchResult matchResult = MatchResult.builder()
                .jobPosting(jobPosting)
                .applicant(applicant)
                .build();
        entityManager.persist(matchResult);
        return matchResult;
    }

    private Notification persistNotification(
            Company company,
            MatchResult matchResult,
            String message
    ) {
        Notification notification = Notification.builder()
                .company(company)
                .matchResult(matchResult)
                .type(NotificationType.RESUME_MATCH_TALENT)
                .message(message)
                .build();
        entityManager.persist(notification);
        return notification;
    }

    private void updateCreateTime(Long notificationId, LocalDateTime createTime) {
        entityManager.createNativeQuery("""
                        UPDATE notifications
                        SET create_time = :createTime
                        WHERE notification_id = :notificationId
                        """)
                .setParameter("createTime", createTime)
                .setParameter("notificationId", notificationId)
                .executeUpdate();
    }
}

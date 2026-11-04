package com.weiver.applicant.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.type.ProfileSyncStatus;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ApplicantRepositoryTest.JpaAuditingTestConfig.class)
class ApplicantRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class JpaAuditingTestConfig {
        @Bean
        public JPAQueryFactory jpaQueryFactory(EntityManager em) {
            return new JPAQueryFactory(em);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ApplicantRepository applicantRepository;

    @Test
    @DisplayName("프로필 동기화 대기 집계: REQUESTED 지원자만 세고, 최장 대기 시각은 요청 시각이 있는 행 중 가장 이른 값이다")
    void profileSyncPendingAggregation_CountsRequestedAndFindsOldestIgnoringNull() {
        // given
        OffsetDateTime oldest = OffsetDateTime.of(2026, 11, 1, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime newer = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);

        applicantRepository.saveAll(List.of(
                applicant("a1@weiver.com", ProfileSyncStatus.REQUESTED, newer),
                applicant("a2@weiver.com", ProfileSyncStatus.REQUESTED, oldest),
                // 배포 전부터 대기 중이라 요청 시각이 없는 행: 건수에만 들어간다.
                applicant("a3@weiver.com", ProfileSyncStatus.REQUESTED, null),
                // 대기 상태가 아닌 행: 요청 시각이 더 이르더라도 집계에서 빠진다.
                applicant("a4@weiver.com", ProfileSyncStatus.FAILED, oldest.minusDays(1)),
                applicant("a5@weiver.com", ProfileSyncStatus.COMPLETED, oldest.minusDays(2))
        ));

        // when
        long count = applicantRepository.countByProfileSyncStatus(ProfileSyncStatus.REQUESTED);
        Optional<OffsetDateTime> oldestRequestedAt =
                applicantRepository.findOldestProfileSyncRequestedAt(ProfileSyncStatus.REQUESTED);

        // then
        assertThat(count).isEqualTo(3);
        assertThat(oldestRequestedAt).hasValueSatisfying(
                requestedAt -> assertThat(requestedAt).isAtSameInstantAs(oldest));
    }

    @Test
    @DisplayName("프로필 동기화 대기 집계: 대기 지원자가 없으면 건수는 0이고 최장 대기 시각은 없다")
    void profileSyncPendingAggregation_ReturnsZeroAndEmpty_WhenNothingPending() {
        // given
        applicantRepository.save(applicant("done@weiver.com", ProfileSyncStatus.COMPLETED, OffsetDateTime.now()));

        // when
        long count = applicantRepository.countByProfileSyncStatus(ProfileSyncStatus.REQUESTED);
        Optional<OffsetDateTime> oldestRequestedAt =
                applicantRepository.findOldestProfileSyncRequestedAt(ProfileSyncStatus.REQUESTED);

        // then
        assertThat(count).isZero();
        assertThat(oldestRequestedAt).isEmpty();
    }

    private Applicant applicant(String email, ProfileSyncStatus status, OffsetDateTime requestedAt) {
        return Applicant.builder()
                .email(email)
                .password("encodedPassword")
                .profileSyncStatus(status)
                .profileSyncRequestedAt(requestedAt)
                .build();
    }
}

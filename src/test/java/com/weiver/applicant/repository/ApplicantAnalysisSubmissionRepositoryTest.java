package com.weiver.applicant.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAnalysisSubmission;
import com.weiver.applicant.type.AnalysisSubmissionStatus;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ApplicantAnalysisSubmissionRepositoryTest.JpaAuditingTestConfig.class)
class ApplicantAnalysisSubmissionRepositoryTest {

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

    private static final Duration DEADLINE = Duration.ofMinutes(30);

    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private ApplicantAnalysisSubmissionRepository submissionRepository;
    @Autowired private EntityManager entityManager;

    @Test
    @DisplayName("대기 집계: REQUESTED 제출 건만 세고, 최장 대기 시각은 그중 가장 이른 요청 시각이다")
    void pendingAggregation_CountsRequestedAndFindsOldest() {
        // given
        OffsetDateTime oldest = OffsetDateTime.of(2026, 11, 1, 9, 0, 0, 0, ZoneOffset.UTC);
        Applicant a1 = applicantRepository.save(applicant("a1@weiver.com"));
        Applicant a2 = applicantRepository.save(applicant("a2@weiver.com"));
        Applicant a3 = applicantRepository.save(applicant("a3@weiver.com"));
        submissionRepository.saveAll(List.of(
                submission(a1, 1, AnalysisSubmissionStatus.REQUESTED, oldest.plusHours(1)),
                submission(a2, 1, AnalysisSubmissionStatus.REQUESTED, oldest),
                // 대기 상태가 아닌 행: 요청 시각이 더 이르더라도 집계에서 빠진다.
                submission(a3, 1, AnalysisSubmissionStatus.FAILED, oldest.minusDays(1)),
                submission(a3, 2, AnalysisSubmissionStatus.COMPLETED, oldest.minusDays(2))
        ));

        // when
        long count = submissionRepository.countByStatus(AnalysisSubmissionStatus.REQUESTED);
        Optional<OffsetDateTime> oldestRequestedAt =
                submissionRepository.findOldestRequestedAt(AnalysisSubmissionStatus.REQUESTED);

        // then
        assertThat(count).isEqualTo(2);
        assertThat(oldestRequestedAt).hasValueSatisfying(
                requestedAt -> assertThat(requestedAt).isAtSameInstantAs(oldest));
    }

    @Test
    @DisplayName("대기 집계: 대기 제출 건이 없으면 건수는 0이고 최장 대기 시각은 없다")
    void pendingAggregation_ReturnsZeroAndEmpty_WhenNothingPending() {
        // given
        Applicant applicant = applicantRepository.save(applicant("done@weiver.com"));
        submissionRepository.save(submission(applicant, 1, AnalysisSubmissionStatus.COMPLETED, OffsetDateTime.now()));

        // when & then
        assertThat(submissionRepository.countByStatus(AnalysisSubmissionStatus.REQUESTED)).isZero();
        assertThat(submissionRepository.findOldestRequestedAt(AnalysisSubmissionStatus.REQUESTED)).isEmpty();
    }

    @Test
    @DisplayName("같은 지원자에게 같은 submission_version을 두 번 저장하면 유니크 제약이 막는다")
    void uniqueApplicantVersion_RejectsDuplicate() {
        // given
        Applicant applicant = applicantRepository.save(applicant("dup@weiver.com"));
        submissionRepository.saveAndFlush(submission(applicant, 1, AnalysisSubmissionStatus.REQUESTED, OffsetDateTime.now()));

        // when & then
        assertThatThrownBy(() -> submissionRepository.saveAndFlush(
                submission(applicant, 1, AnalysisSubmissionStatus.REQUESTED, OffsetDateTime.now())))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("스냅샷은 jsonb로 저장되고 null 값과 중첩 구조를 그대로 다시 읽는다")
    void snapshot_RoundTripsAsJsonb() {
        // given
        Applicant applicant = applicantRepository.save(applicant("json@weiver.com"));
        Map<String, Object> file = new java.util.LinkedHashMap<>();
        file.put("key", "portfolios/a.pdf");
        file.put("version_id", null);
        Map<String, Object> snapshot = Map.of(
                "submission_version", 1,
                "awards", List.of(),
                "portfolio", Map.of("portfolio_id", 12, "file", file));
        submissionRepository.saveAndFlush(ApplicantAnalysisSubmission.request(
                applicant, 1, "event-json", snapshot, "a".repeat(64), OffsetDateTime.now(), DEADLINE));
        entityManager.clear();

        // when
        ApplicantAnalysisSubmission found = submissionRepository
                .findByApplicantAndSubmissionVersion(applicantRepository.findById(applicant.getApplicantId()).orElseThrow(), 1)
                .orElseThrow();

        // then
        assertThat(found.getSnapshot()).containsEntry("submission_version", 1);
        assertThat(found.getSnapshot().get("awards")).isEqualTo(List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> foundFile = (Map<String, Object>) ((Map<String, Object>) found.getSnapshot().get("portfolio")).get("file");
        assertThat(foundFile).containsEntry("version_id", null);
        assertThat(found.getEventId()).isEqualTo("event-json");
        assertThat(found.getDeadlineAt()).isAtSameInstantAs(found.getRequestedAt().plus(DEADLINE));
    }

    private Applicant applicant(String email) {
        return Applicant.builder()
                .email(email)
                .password("encodedPassword")
                .build();
    }

    private ApplicantAnalysisSubmission submission(Applicant applicant, int version,
                                                   AnalysisSubmissionStatus status, OffsetDateTime requestedAt) {
        return ApplicantAnalysisSubmission.builder()
                .applicant(applicant)
                .submissionVersion(version)
                .eventId(java.util.UUID.randomUUID().toString())
                .snapshot(Map.of("submission_version", version))
                .contentHash("a".repeat(64))
                .status(status)
                .requestedAt(requestedAt)
                .deadlineAt(requestedAt.plus(DEADLINE))
                .build();
    }
}

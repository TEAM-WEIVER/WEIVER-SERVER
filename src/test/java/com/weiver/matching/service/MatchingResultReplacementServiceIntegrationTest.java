package com.weiver.matching.service;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.applicant.domain.Applicant;
import com.weiver.company.domain.Company;
import com.weiver.company.type.*;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.event.dto.MatchingCompletedData;
import com.weiver.matching.repository.MatchingProcessingLockRepository;
import com.weiver.matching.repository.MatchResultRepository;
import com.weiver.notification.domain.Notification;
import com.weiver.notification.dto.response.NotificationResponseDTO;
import com.weiver.notification.repository.NotificationRepository;
import com.weiver.notification.service.NotificationScheduler;
import com.weiver.notification.type.NotificationType;
import jakarta.persistence.EntityManager;
import org.hibernate.SessionFactory;
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
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.sql.SQLException;
import java.sql.DriverManager;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MatchingResultReplacementService.class, NotificationScheduler.class, MatchingProcessingLockRepository.class,
        MatchingResultReplacementServiceIntegrationTest.Config.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class MatchingResultReplacementServiceIntegrationTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class Config {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired private MatchingResultReplacementService replacementService;
    @Autowired private NotificationScheduler scheduler;
    @Autowired private NotificationRepository notificationRepository;
    @Autowired private EntityManager entityManager;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private MatchingProcessingLockRepository processingLockRepository;
    @Autowired private MatchResultRepository matchResultRepository;

    @Test
    @DisplayName("결과를 교체해도 기존 알림을 보존하고 같은 이벤트 재수신은 새 알림을 만들지 않는다")
    void replace_PreservesNotificationsAndSkipsRedelivery() {
        // given
        Fixture fixture = fixture();
        String eventId = UUID.randomUUID().toString();

        // when
        replacementService.replace(eventId, data(fixture, 90f));
        scheduler.generateGroupedMatchingNotifications();
        Long newMatchId = currentMatchId(fixture);
        replacementService.replace(eventId, data(fixture, 20f));
        scheduler.generateGroupedMatchingNotifications();

        // then
        assertThat(currentMatchId(fixture)).isEqualTo(newMatchId).isNotEqualTo(fixture.matchId());
        var notifications = notificationRepository.findSliceByCompanyPublicId(
                fixture.companyPublicId(), PageRequest.of(0, 20));
        assertThat(notifications.getContent()).hasSize(2);
        Notification old = notifications.stream()
                .filter(n -> n.getNotificationId().equals(fixture.notificationId())).findFirst().orElseThrow();
        assertThat(old.getMatchId()).isEqualTo(fixture.matchId());
        assertThat(old.getIsRead()).isTrue();
        assertThat(old.getMessage()).isEqualTo("기존 알림");
        assertThat(NotificationResponseDTO.from(old).jdId()).isEqualTo(fixture.jdId());
        var counts = notificationRepository.countNewApplicantsByJdIds(List.of(fixture.jdId()));
        assertThat(counts).hasSize(1);
        assertThat(counts.getFirst()[1]).isEqualTo(1L);
    }

    @Test
    @DisplayName("교체 실패는 처리 기록도 롤백하므로 같은 event_id로 정상 처리할 수 있다")
    void replace_RollsBackEventRegistrationOnFailure() {
        // given
        Fixture fixture = fixture();
        String eventId = UUID.randomUUID().toString();
        var invalid = new MatchingCompletedData(fixture.jdId(), List.of(
                new MatchingCompletedData.MatchData(Long.MAX_VALUE, 1f, 1f, 1f, "없는 지원자")));

        // when / then
        assertThatThrownBy(() -> replacementService.replace(eventId, invalid))
                .isInstanceOf(BusinessException.class).extracting("code")
                .isEqualTo(ErrorCode.APPLICANT_NOT_FOUND);
        assertThat(currentMatchId(fixture)).isEqualTo(fixture.matchId());
        replacementService.replace(eventId, data(fixture, 90f));
        assertThat(currentMatchId(fixture)).isNotEqualTo(fixture.matchId());
    }

    @Test
    @DisplayName("같은 event_id를 동시에 수신해도 처리 기록과 결과 교체는 한 번만 커밋된다")
    void replace_DeduplicatesConcurrentDeliveries() throws Exception {
        // given
        Fixture fixture = fixture();
        String eventId = UUID.randomUUID().toString();
        CyclicBarrier start = new CyclicBarrier(2);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var task = (java.util.concurrent.Callable<Void>) () -> {
                start.await(10, TimeUnit.SECONDS);
                replacementService.replace(eventId, data(fixture, 90f));
                return null;
            };

            // when
            var first = executor.submit(task);
            var second = executor.submit(task);
            first.get(20, TimeUnit.SECONDS);
            second.get(20, TimeUnit.SECONDS);
        }

        // then
        Long processedCount = new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery("SELECT COUNT(e) FROM MatchingProcessedEvent e WHERE e.eventId = :id", Long.class)
                        .setParameter("id", eventId).getSingleResult());
        assertThat(processedCount).isEqualTo(1L);
        assertThat(currentMatchId(fixture)).isNotEqualTo(fixture.matchId());
    }

    @Test
    @DisplayName("공고별 집계는 미알림만 세고 최신 시각이 같으면 큰 ID를 대표로 선택한다")
    void scheduler_AggregatesUnnotifiedAndChoosesLatestRepresentative() {
        // given
        Fixture fixture = fixture();
        LocalDateTime latest = LocalDateTime.of(2026, 11, 6, 12, 0);
        Long representativeId = new TransactionTemplate(transactionManager).execute(status -> {
            persistUnnotifiedMatch(fixture, latest);
            Long representative = persistUnnotifiedMatch(fixture, latest);
            persistUnnotifiedMatch(fixture, latest.minusHours(1));
            return representative;
        });

        // when
        var statistics = entityManager.getEntityManagerFactory().unwrap(SessionFactory.class).getStatistics();
        long beforeAggregate = statistics.getPrepareStatementCount();
        var groups = matchResultRepository.findUnnotifiedGroups();
        assertThat(statistics.getPrepareStatementCount() - beforeAggregate).isEqualTo(1L);
        var group = groups.stream()
                .filter(g -> g.jdId().equals(fixture.jdId())).findFirst().orElseThrow();
        long beforeBatch = statistics.getPrepareStatementCount();
        scheduler.generateGroupedMatchingNotifications();
        // 잠금 SELECT + 집계 SELECT + 공고별 알림 INSERT + 벌크 UPDATE. 회사 프록시는 SELECT하지 않는다.
        assertThat(statistics.getPrepareStatementCount() - beforeBatch).isEqualTo(groups.size() + 3L);

        // then
        assertThat(group.matchCount()).isEqualTo(3L);
        assertThat(group.title()).isEqualTo("공고");
        assertThat(group.latestCreateTime()).isEqualTo(latest);
        assertThat(group.representativeMatchId()).isEqualTo(representativeId);
        var notifications = notificationRepository.findSliceByCompanyPublicId(
                fixture.companyPublicId(), PageRequest.of(0, 20));
        assertThat(notifications.getContent()).hasSize(2);
        Notification generated = notifications.stream().filter(n -> !n.getIsRead()).findFirst().orElseThrow();
        assertThat(generated.getMessage()).isEqualTo("[공고] 공고에 3건의 새로운 매칭이 있습니다.");
        assertThat(generated.getMatchId()).isEqualTo(representativeId);
        assertThat(matchResultRepository.findUnnotifiedGroups()).noneMatch(g -> g.jdId().equals(fixture.jdId()));
    }

    @Test
    @DisplayName("다른 트랜잭션이 잠금을 보유하면 배치를 건너뛰고 해제 후 정규 실행에서 처리한다")
    void scheduler_SkipsContendedLockThenProcessesAfterRelease() {
        // given
        Fixture fixture = fixture();
        markPending(fixture);
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                processingLockRepository.lock();

                // when: 별도 연결에서 실행되는 스케줄러는 즉시 종료한다.
                var skipped = executor.submit(scheduler::generateGroupedMatchingNotifications);
                try {
                    skipped.get(5, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new AssertionError("잠금 경합 시 배치는 대기하지 않아야 합니다.", e);
                }

                // then
                assertThat(notificationCount(fixture)).isEqualTo(1L);
                assertThat(matchResultRepository.findUnnotifiedGroups())
                        .anyMatch(g -> g.jdId().equals(fixture.jdId()));
            });
        }
        scheduler.generateGroupedMatchingNotifications();
        assertThat(notificationCount(fixture)).isEqualTo(2L);
    }

    @Test
    @DisplayName("배치 중 결과 교체는 잠금을 기다리고 교체된 결과는 다음 배치에서 알림 처리된다")
    void replacement_WaitsForBatchAndLeavesNewResultsForNextBatch() throws Exception {
        // given
        Fixture fixture = fixture();
        markPending(fixture);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var replacement = new TransactionTemplate(transactionManager).execute(status -> {
                processingLockRepository.lock();
                var future = executor.submit(() -> replacementService.replace(
                        UUID.randomUUID().toString(), data(fixture, 90f)));
                awaitAdvisoryWaiter();
                assertThat(future.isDone()).isFalse();

                // when: 잠금을 보유한 배치가 기존 결과를 알림 처리한다.
                scheduler.generateGroupedMatchingNotifications();
                assertThat(currentMatchId(fixture)).isEqualTo(fixture.matchId());
                return future;
            });
            replacement.get(10, TimeUnit.SECONDS);
        }

        // then
        assertThat(currentMatchId(fixture)).isNotEqualTo(fixture.matchId());
        assertThat(notificationCount(fixture)).isEqualTo(2L);
        assertThat(matchResultRepository.findUnnotifiedGroups())
                .anyMatch(g -> g.jdId().equals(fixture.jdId()));
        scheduler.generateGroupedMatchingNotifications();
        assertThat(notificationCount(fixture)).isEqualTo(3L);
        assertThat(matchResultRepository.findUnnotifiedGroups())
                .noneMatch(g -> g.jdId().equals(fixture.jdId()));
    }

    @Test
    @DisplayName("배치 트랜잭션 실패 시 생성 알림과 벌크 상태 갱신이 함께 롤백된다")
    void scheduler_RollsBackNotificationsAndBulkUpdateTogether() {
        // given
        Fixture fixture = fixture();
        markPending(fixture);

        // when
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            scheduler.generateGroupedMatchingNotifications();
            status.setRollbackOnly();
        });

        // then
        assertThat(notificationCount(fixture)).isEqualTo(1L);
        assertThat(matchResultRepository.findUnnotifiedGroups())
                .anyMatch(g -> g.jdId().equals(fixture.jdId()));
        scheduler.generateGroupedMatchingNotifications();
        assertThat(notificationCount(fixture)).isEqualTo(2L);
    }

    @Test
    @DisplayName("잠금 대기 제한 초과 시 처리 기록도 롤백되고 같은 이벤트로 다시 처리할 수 있다")
    void replacement_TimesOutAndRollsBackRegistration() {
        // given
        Fixture fixture = fixture();
        String eventId = UUID.randomUUID().toString();
        try (var executor = Executors.newSingleThreadExecutor()) {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                processingLockRepository.lock();
                var blocked = executor.submit(() -> replacementService.replace(eventId, data(fixture, 90f)));
                awaitAdvisoryWaiter();

                // when / then: 설정된 대기 제한으로 실패하며 임의 재시도는 실행하지 않는다.
                assertThatThrownBy(() -> blocked.get(15, TimeUnit.SECONDS))
                        .isInstanceOf(ExecutionException.class)
                        .satisfies(error -> {
                            Throwable root = error;
                            while (root.getCause() != null) {
                                root = root.getCause();
                            }
                            assertThat(root).isInstanceOf(SQLException.class);
                            assertThat(((SQLException) root).getSQLState()).isEqualTo("55P03");
                        });
                assertThat(currentMatchId(fixture)).isEqualTo(fixture.matchId());
            });
        }

        Long processedCount = new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery("SELECT COUNT(e) FROM MatchingProcessedEvent e WHERE e.eventId = :id", Long.class)
                        .setParameter("id", eventId).getSingleResult());
        assertThat(processedCount).isZero();
        replacementService.replace(eventId, data(fixture, 90f));
        assertThat(currentMatchId(fixture)).isNotEqualTo(fixture.matchId());
    }

    @Test
    @DisplayName("잠금 획득 후 같은 트랜잭션의 기존 lock_timeout 값을 복원한다")
    void lock_RestoresPreviousTimeout() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            // given
            entityManager.createNativeQuery("SELECT set_config('lock_timeout', '3s', true)").getSingleResult();

            // when
            processingLockRepository.lock();

            // then
            assertThat(entityManager.createNativeQuery("SELECT current_setting('lock_timeout')").getSingleResult())
                    .isEqualTo("3s");
        });
    }

    @Test
    @DisplayName("기존 FK와 알림이 있는 스키마에 필수 전환 SQL을 재실행해도 데이터와 중복 차단을 보존한다")
    void deploymentSql_TransitionsLegacySchemaAndIsRepeatable() throws Exception {
        // given: 애플리케이션 테이블과 격리된 테스트 전용 구버전 스키마.
        String schema = "legacy187_" + UUID.randomUUID().toString().replace("-", "");
        String sql = Files.readString(Path.of("docs/sql/matching-187-transition.sql"));
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
             var statement = connection.createStatement()) {
            statement.execute("CREATE SCHEMA " + schema);
            try {
                statement.execute("SET search_path TO " + schema);
                statement.execute("CREATE TABLE match_results (match_id bigint PRIMARY KEY, jd_id bigint NOT NULL)");
                statement.execute("""
                        CREATE TABLE notifications (
                            notification_id bigint PRIMARY KEY,
                            match_id bigint NOT NULL REFERENCES match_results(match_id),
                            message text NOT NULL,
                            is_read boolean NOT NULL
                        )
                        """);
                statement.executeUpdate("INSERT INTO match_results VALUES (10, 100), (20, 200)");
                statement.executeUpdate("INSERT INTO notifications VALUES (1, 10, 'legacy one', true), (2, 20, 'legacy two', false)");

                // when: 운영용 파일 자체를 두 번 실행한다.
                statement.execute(sql);
                statement.execute(sql);

                // then: 기존 값·읽음 상태를 보존하고 jd_id를 채웠다.
                try (var rows = statement.executeQuery("SELECT match_id, jd_id, message, is_read FROM notifications ORDER BY notification_id")) {
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("match_id")).isEqualTo(10L);
                    assertThat(rows.getLong("jd_id")).isEqualTo(100L);
                    assertThat(rows.getString("message")).isEqualTo("legacy one");
                    assertThat(rows.getBoolean("is_read")).isTrue();
                    assertThat(rows.next()).isTrue();
                    assertThat(rows.getLong("jd_id")).isEqualTo(200L);
                    assertThat(rows.getBoolean("is_read")).isFalse();
                    assertThat(rows.next()).isFalse();
                }
                try (var constraints = statement.executeQuery("""
                        SELECT COUNT(*) FROM pg_constraint
                        WHERE conrelid = 'notifications'::regclass
                          AND confrelid = 'match_results'::regclass AND contype = 'f'
                        """)) {
                    assertThat(constraints.next()).isTrue();
                    assertThat(constraints.getLong(1)).isZero();
                }
                try (var columns = statement.executeQuery("""
                        SELECT attnotnull FROM pg_attribute
                        WHERE attrelid = 'notifications'::regclass AND attname = 'jd_id'
                        """)) {
                    assertThat(columns.next()).isTrue();
                    assertThat(columns.getBoolean(1)).isTrue();
                }
                assertThat(statement.executeUpdate("DELETE FROM match_results WHERE match_id = 10")).isEqualTo(1);
                statement.executeUpdate("INSERT INTO matching_processed_events (event_id, create_time) VALUES ('legacy-event', CURRENT_TIMESTAMP)");
                assertThatThrownBy(() -> statement.executeUpdate(
                        "INSERT INTO matching_processed_events (event_id, create_time) VALUES ('legacy-event', CURRENT_TIMESTAMP)"))
                        .isInstanceOf(SQLException.class).extracting(error -> ((SQLException) error).getSQLState())
                        .isEqualTo("23505");
            } finally {
                statement.execute("ROLLBACK");
                statement.execute("DROP SCHEMA " + schema + " CASCADE");
            }
        }
    }

    private void awaitAdvisoryWaiter() {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            Number waiting = (Number) entityManager.createNativeQuery("""
                    SELECT COUNT(*) FROM pg_locks
                    WHERE locktype = 'advisory' AND NOT granted
                      AND database = (SELECT oid FROM pg_database WHERE datname = current_database())
                    """).getSingleResult();
            if (waiting.longValue() > 0) {
                return;
            }
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("잠금 대기 확인이 중단됐습니다.", e);
            }
        }
        throw new AssertionError("결과 교체가 advisory lock을 기다리지 않았습니다.");
    }

    private Long notificationCount(Fixture fixture) {
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery("SELECT COUNT(n) FROM Notification n WHERE n.jdId = :id", Long.class)
                        .setParameter("id", fixture.jdId()).getSingleResult());
    }

    private void markPending(Fixture fixture) {
        new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                entityManager.createQuery("UPDATE MatchResult m SET m.isNotified = false WHERE m.matchId = :id")
                        .setParameter("id", fixture.matchId()).executeUpdate());
    }

    private Long persistUnnotifiedMatch(Fixture fixture, LocalDateTime createTime) {
        String suffix = UUID.randomUUID().toString();
        Applicant applicant = Applicant.builder().email(suffix + "@test.com").password("encoded").build();
        entityManager.persist(applicant);
        MatchResult match = MatchResult.builder().jobPosting(entityManager.getReference(JobPosting.class, fixture.jdId()))
                .applicant(applicant).build();
        entityManager.persist(match);
        entityManager.createNativeQuery("UPDATE match_results SET create_time = :time WHERE match_id = :id")
                .setParameter("time", createTime).setParameter("id", match.getMatchId()).executeUpdate();
        return match.getMatchId();
    }

    private MatchingCompletedData data(Fixture fixture, float score) {
        return new MatchingCompletedData(fixture.jdId(), List.of(
                new MatchingCompletedData.MatchData(fixture.applicantId(), score, score, score, "새 결과")));
    }

    private Long currentMatchId(Fixture fixture) {
        return new TransactionTemplate(transactionManager).execute(status ->
                entityManager.createQuery("SELECT m.matchId FROM MatchResult m WHERE m.jobPosting.jdId = :id", Long.class)
                        .setParameter("id", fixture.jdId()).getSingleResult());
    }

    private Fixture fixture() {
        return new TransactionTemplate(transactionManager).execute(status -> {
            String suffix = UUID.randomUUID().toString();
            Company company = Company.builder().publicId(suffix).loginId(suffix).password("encoded")
                    .companyType(CompanyType.STARTUP).employeeNum(10).companyCeoName("대표")
                    .companyName("기업").foundedYear(LocalDate.of(2020, 1, 1)).avgSale(100)
                    .address("서울").cultureDescription("문화").directionDescription("방향")
                    .workPace(WorkPace.FAST_EXECUTION).decisionMaking(DecisionMaking.TEAM_CONSENSUS)
                    .roleDefinition(RoleDefinition.CLEAR_RESPONSIBILITY)
                    .operationStyle(OperationStyle.STABILITY_ORIENTED).build();
            entityManager.persist(company);
            JobPosting job = JobPosting.builder().company(company).title("공고").jobCategory("IT")
                    .detailedJob("backend").deadline(LocalDate.now().plusDays(30))
                    .status(JobPostingStatus.ACTIVE).build();
            entityManager.persist(job);
            Applicant applicant = Applicant.builder().publicId(suffix).email(suffix + "@test.com")
                    .password("encoded").build();
            entityManager.persist(applicant);
            MatchResult match = MatchResult.builder().jobPosting(job).applicant(applicant).isNotified(true).build();
            entityManager.persist(match);
            Notification notification = Notification.builder().company(company).matchId(match.getMatchId())
                    .jdId(job.getJdId()).message("기존 알림").type(NotificationType.RESUME_MATCH_TALENT)
                    .isRead(true).build();
            entityManager.persist(notification);
            return new Fixture(company.getPublicId(), job.getJdId(), applicant.getApplicantId(),
                    match.getMatchId(), notification.getNotificationId());
        });
    }

    private record Fixture(String companyPublicId, Long jdId, Long applicantId, Long matchId, Long notificationId) {}
}

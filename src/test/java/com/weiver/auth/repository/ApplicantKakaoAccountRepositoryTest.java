package com.weiver.auth.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.auth.domain.ApplicantKakaoAccount;
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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers
@DataJpaTest(properties = "spring.jpa.hibernate.ddl-auto=create-drop")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ApplicantKakaoAccountRepositoryTest.JpaAuditingTestConfig.class)
class ApplicantKakaoAccountRepositoryTest {

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
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private ApplicantKakaoAccountRepository repository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Test
    @DisplayName("비밀번호가 없는 지원자의 카카오 연결을 양쪽 식별자로 다시 조회할 수 있다")
    void findAccountByKakaoUserIdAndApplicantId() {
        Applicant applicant = applicantRepository.saveAndFlush(Applicant.builder().email("social@example.com").build());
        ApplicantKakaoAccount account = repository.saveAndFlush(ApplicantKakaoAccount.builder()
                .applicant(applicant)
                .kakaoUserId(93031L)
                .build());
        entityManager.clear();

        assertThat(repository.findByKakaoUserId(93031L)).hasValueSatisfying(found -> {
            assertThat(found.getApplicant().getPublicId()).isEqualTo(applicant.getPublicId());
            assertThat(found.getApplicant().getPassword()).isNull();
        });
        assertThat(repository.findByApplicant_ApplicantId(applicant.getApplicantId()))
                .hasValueSatisfying(found -> assertThat(found.getId()).isEqualTo(account.getId()));
        assertThat(repository.existsByApplicant_ApplicantId(applicant.getApplicantId())).isTrue();
    }

    @Test
    @DisplayName("한 지원자에 두 카카오 계정을 연결하면 지원자 외래 키의 유일성 제약이 거절한다")
    void rejectSecondKakaoAccountForApplicant() {
        Applicant applicant = applicantRepository.saveAndFlush(Applicant.builder().email("linked@example.com").build());
        repository.saveAndFlush(ApplicantKakaoAccount.builder().applicant(applicant).kakaoUserId(93032L).build());

        assertThatThrownBy(() -> repository.saveAndFlush(
                ApplicantKakaoAccount.builder().applicant(applicant).kakaoUserId(93033L).build()))
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo("23505"));
    }

    @Test
    @DisplayName("같은 카카오 ID로 동시 가입하면 한 트랜잭션만 지원자와 연결을 저장한다")
    void onlyOneSignupCanCommitForSameKakaoUserId() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Boolean> first = executor.submit(() -> trySignup("first@example.com", ready, start));
            Future<Boolean> second = executor.submit(() -> trySignup("second@example.com", ready, start));
            assertThat(ready.await(5, SECONDS)).isTrue();
            start.countDown();

            boolean firstCommitted = first.get(5, SECONDS);
            boolean secondCommitted = second.get(5, SECONDS);
            assertThat(new Boolean[]{firstCommitted, secondCommitted})
                    .containsExactlyInAnyOrder(true, false);
            String winner = firstCommitted ? "first@example.com" : "second@example.com";
            String loser = firstCommitted ? "second@example.com" : "first@example.com";
            assertThat(repository.findByKakaoUserId(93034L))
                    .hasValueSatisfying(found -> assertThat(found.getApplicant().getEmail()).isEqualTo(winner));
            assertThat(applicantRepository.findByEmailAndDeletedFalse(loser)).isEmpty();
        } finally {
            executor.shutdownNow();
        }
    }

    private boolean trySignup(String email, CountDownLatch ready, CountDownLatch start) {
        try {
            new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
                Applicant applicant = applicantRepository.saveAndFlush(Applicant.builder().email(email).build());
                ready.countDown();
                try {
                    assertThat(start.await(5, SECONDS)).isTrue();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new AssertionError(e);
                }
                repository.saveAndFlush(ApplicantKakaoAccount.builder().applicant(applicant).kakaoUserId(93034L).build());
            });
            return true;
        } catch (DataIntegrityViolationException e) {
            assertThat(e).rootCause().isInstanceOfSatisfying(
                    SQLException.class, cause -> assertThat(cause.getSQLState()).isEqualTo("23505"));
            return false;
        }
    }
}

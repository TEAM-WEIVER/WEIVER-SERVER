package com.weiver.applicant.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.analysis.event.ApplicantAnalysisEventService;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.ApplicantAnalysisSubmission;
import com.weiver.applicant.domain.Education;
import com.weiver.applicant.repository.ApplicantAnalysisSubmissionRepository;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.applicant.repository.EducationRepository;
import com.weiver.applicant.type.AnalysisSubmissionStatus;
import com.weiver.applicant.type.Degree;
import com.weiver.applicant.type.Status;
import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.domain.EssayQuestion;
import com.weiver.essay.repository.EssayAnswerRepository;
import com.weiver.essay.repository.EssayQuestionRepository;
import com.weiver.global.event.dto.EventEnvelope;
import com.weiver.global.event.dto.EventType;
import com.weiver.global.event.publisher.DomainEventPublisher;
import com.weiver.global.exception.BusinessException;
import com.weiver.global.exception.ErrorCode;
import com.weiver.global.s3.service.S3Service;
import com.weiver.portfolio.domain.Portfolio;
import com.weiver.portfolio.repository.PortfolioRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 동시 제출이 버전을 두 번 발급하지 않는지 실제 PostgreSQL 행 잠금으로 확인한다.
 * 테스트 트랜잭션을 끄고, 두 트랜잭션을 서로 다른 스레드에서 TransactionTemplate으로 실행한다.
 */
@Testcontainers
@DataJpaTest(properties = "weiver.applicant-analysis.deadline=30m")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({
        ApplicantSubmitConcurrencyTest.TestConfig.class,
        ApplicantService.class,
        ApplicantAnalysisSubmissionService.class,
        ApplicantAnalysisSnapshotService.class,
        ApplicantAnalysisEventService.class
})
class ApplicantSubmitConcurrencyTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class TestConfig {
        @Bean
        JPAQueryFactory jpaQueryFactory(EntityManager em) {
            return new JPAQueryFactory(em);
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper().findAndRegisterModules()
                    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @MockitoBean private DomainEventPublisher domainEventPublisher;
    @MockitoBean private S3Service s3Service;
    @MockitoBean private WorkExperienceService workExperienceService;

    @Autowired private ApplicantService applicantService;
    @Autowired private ApplicantRepository applicantRepository;
    @Autowired private ApplicantAnalysisSubmissionRepository submissionRepository;
    @Autowired private EducationRepository educationRepository;
    @Autowired private EssayQuestionRepository essayQuestionRepository;
    @Autowired private EssayAnswerRepository essayAnswerRepository;
    @Autowired private PortfolioRepository portfolioRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private final ExecutorService executor = Executors.newFixedThreadPool(2);

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
        submissionRepository.deleteAllInBatch();
        portfolioRepository.deleteAllInBatch();
        essayAnswerRepository.deleteAllInBatch();
        essayQuestionRepository.deleteAllInBatch();
        educationRepository.deleteAllInBatch();
        applicantRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("첫 제출이 지원자 행을 잠근 동안 두 번째 제출은 기다렸다가 PROFILE_ALREADY_SUBMITTED로 거절되고, 버전은 1개만 발급된다")
    void concurrentSubmit_IssuesSingleVersion() throws Exception {
        // given
        String publicId = givenSubmittableApplicant();
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        CountDownLatch firstSubmitted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);

        // when: 첫 트랜잭션은 제출까지 마친 뒤 커밋하지 않고 잠금을 쥐고 기다린다.
        CompletableFuture<Void> first = CompletableFuture.runAsync(() ->
                transactionTemplate.executeWithoutResult(status -> {
                    applicantService.submitProfile(publicId);
                    firstSubmitted.countDown();
                    await(releaseFirst);
                }), executor);
        assertThat(firstSubmitted.await(10, TimeUnit.SECONDS)).isTrue();

        CompletableFuture<Void> second = CompletableFuture.runAsync(() ->
                transactionTemplate.executeWithoutResult(status -> applicantService.submitProfile(publicId)),
                executor);

        // then: 두 번째 트랜잭션은 지원자 행 잠금에서 막혀 끝나지 않는다.
        assertThatThrownBy(() -> second.get(1, TimeUnit.SECONDS)).isInstanceOf(TimeoutException.class);

        releaseFirst.countDown();
        first.get(10, TimeUnit.SECONDS);

        // 잠금이 풀리면 커밋된 REQUESTED 제출 건을 보고 거절한다(유니크 제약 위반이 아니라 상태 확인으로 막힌다).
        assertThatThrownBy(() -> second.get(10, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .cause()
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.PROFILE_ALREADY_SUBMITTED);

        List<ApplicantAnalysisSubmission> submissions = submissionRepository.findAll();
        assertThat(submissions).hasSize(1);
        assertThat(submissions.getFirst().getSubmissionVersion()).isEqualTo(1);
        assertThat(submissions.getFirst().getStatus()).isEqualTo(AnalysisSubmissionStatus.REQUESTED);
        assertThat(applicantRepository.findByPublicId(publicId).orElseThrow().getCurrentSubmissionVersion())
                .isEqualTo(1);

        // 분석 요청은 1개만, 커밋 뒤 발행 경로로 나간다.
        ArgumentCaptor<EventEnvelope<?>> captor = ArgumentCaptor.forClass(EventEnvelope.class);
        verify(domainEventPublisher, times(1)).publishAfterCommit(captor.capture());
        assertThat(captor.getValue().eventType()).isEqualTo(EventType.APPLICANT_ANALYSIS_REQUESTED);
        assertThat(captor.getValue().eventId()).isEqualTo(submissions.getFirst().getEventId());
    }

    private String givenSubmittableApplicant() {
        Applicant applicant = applicantRepository.save(Applicant.builder()
                .email("concurrent@weiver.com")
                .password("encodedPassword")
                .name("홍길동")
                .phoneNumber("010-1234-5678")
                .birthday(LocalDate.of(2000, 1, 1))
                .build());
        educationRepository.save(Education.builder()
                .degree(Degree.BACHELOR).schoolName("한양대학교").major("컴퓨터공학")
                .startDate(YearMonth.of(2021, 3)).status(Status.ACTIVE).applicant(applicant).build());
        EssayQuestion question = essayQuestionRepository.save(EssayQuestion.builder()
                .sequence(1).maxLength(1000).question("지원 동기").build());
        essayAnswerRepository.save(EssayAnswer.builder()
                .answer("답변").applicant(applicant).essayQuestion(question).build());
        portfolioRepository.save(Portfolio.builder()
                .urlGithub("https://github.com/weiver").applicant(applicant).build());
        return applicant.getPublicId();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("latch timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}

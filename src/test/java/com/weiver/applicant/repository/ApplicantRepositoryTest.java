package com.weiver.applicant.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.Award;
import com.weiver.essay.domain.EssayAnswer;
import com.weiver.essay.domain.EssayQuestion;
import com.weiver.portfolio.domain.Portfolio;
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

import java.time.LocalDate;

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
        public JPAQueryFactory jpaQueryFactory(EntityManager entityManager) {
            return new JPAQueryFactory(entityManager);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private ApplicantRepository applicantRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("지원자에게 수상, 자기소개서, 포트폴리오가 있으면 projection의 완료 상태가 모두 true이다.")
    void findDocumentStatusByPublicId_AllDocumentsCompleted_ReturnsAllTrue() {
        // Given
        Applicant applicant = persistCompletedApplicant("applicant-with-documents", "with-documents@example.com");

        entityManager.persist(Award.builder()
                .awardName("백엔드 경진대회 대상")
                .issuer("테스트 기관")
                .awardDate(LocalDate.of(2026, 8, 1))
                .applicant(applicant)
                .build());

        EssayQuestion essayQuestion = EssayQuestion.builder()
                .sequence(1)
                .maxLength(1_000)
                .question("지원 동기를 작성해주세요.")
                .build();
        entityManager.persist(essayQuestion);

        entityManager.persist(EssayAnswer.builder()
                .answer("테스트 자기소개서입니다.")
                .applicant(applicant)
                .essayQuestion(essayQuestion)
                .build());

        entityManager.persist(Portfolio.builder()
                .fileName("portfolio.pdf")
                .fileType("application/pdf")
                .fileKey("portfolios/portfolio.pdf")
                .fileSize(1_024L)
                .uploadedAt(LocalDate.of(2026, 8, 1).atStartOfDay())
                .applicant(applicant)
                .build());

        entityManager.flush();
        entityManager.clear();

        // When
        ApplicantDocumentStatusProjection status = applicantRepository
                .findDocumentStatusByPublicId(applicant.getPublicId())
                .orElseThrow();

        // Then
        assertThat(status.getName()).isEqualTo("이현우");
        assertThat(status.getEmail()).isEqualTo("with-documents@example.com");
        assertThat(status.getPhoneNumber()).isEqualTo("010-1234-5678");
        assertThat(status.getBirthday()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(status.getResumeDetailCompleted()).isTrue();
        assertThat(status.getEssayCompleted()).isTrue();
        assertThat(status.getPortfolioCompleted()).isTrue();
    }

    @Test
    @DisplayName("지원자에게 하위 제출 자료가 없으면 projection의 완료 상태가 모두 false이다.")
    void findDocumentStatusByPublicId_NoDocuments_ReturnsAllFalse() {
        // Given
        Applicant applicant = persistCompletedApplicant("applicant-without-documents", "without-documents@example.com");
        entityManager.flush();
        entityManager.clear();

        // When
        ApplicantDocumentStatusProjection status = applicantRepository
                .findDocumentStatusByPublicId(applicant.getPublicId())
                .orElseThrow();

        // Then
        assertThat(status.getName()).isEqualTo("이현우");
        assertThat(status.getEmail()).isEqualTo("without-documents@example.com");
        assertThat(status.getPhoneNumber()).isEqualTo("010-1234-5678");
        assertThat(status.getBirthday()).isEqualTo(LocalDate.of(2000, 1, 1));
        assertThat(status.getResumeDetailCompleted()).isFalse();
        assertThat(status.getEssayCompleted()).isFalse();
        assertThat(status.getPortfolioCompleted()).isFalse();
    }

    @Test
    @DisplayName("존재하지 않는 publicId로 제출 상태를 조회하면 Optional.empty를 반환한다.")
    void findDocumentStatusByPublicId_ApplicantNotFound_ReturnsEmpty() {
        // When & Then
        assertThat(applicantRepository.findDocumentStatusByPublicId("not-found")).isEmpty();
    }

    private Applicant persistCompletedApplicant(String publicId, String email) {
        Applicant applicant = Applicant.builder()
                .publicId(publicId)
                .email(email)
                .password("encoded-password")
                .name("이현우")
                .phoneNumber("010-1234-5678")
                .birthday(LocalDate.of(2000, 1, 1))
                .build();
        entityManager.persist(applicant);
        return applicant;
    }
}

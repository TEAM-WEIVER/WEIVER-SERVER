package com.weiver.interview.repository;

import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.repository.ApplicantRepository;
import com.weiver.interview.domain.InterviewSession;
import com.weiver.interview.type.InterviewSessionStatus;
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

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(InterviewSessionRepositoryTest.JpaAuditingTestConfig.class)
class InterviewSessionRepositoryTest {

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

    private static final LocalDateTime BASE = LocalDateTime.of(2026, 11, 1, 9, 0);

    @Autowired
    private InterviewSessionRepository interviewSessionRepository;

    @Autowired
    private ApplicantRepository applicantRepository;

    @Test
    @DisplayName("면접 대기 집계: 상태별로 세고, 상태마다 해당 요청 시각 컬럼의 가장 이른 값을 찾으며 null은 건너뛴다")
    void interviewPendingAggregation_CountsByStatusAndFindsOldestPerColumn() {
        // given
        Applicant applicant = applicantRepository.save(Applicant.builder()
                .email("interview@weiver.com")
                .password("encodedPassword")
                .build());

        InterviewSession waitingOld = session(applicant, InterviewSessionStatus.WAITING_FOR_QUESTION,
                BASE, null, null);
        InterviewSession waitingNew = session(applicant, InterviewSessionStatus.WAITING_FOR_QUESTION,
                BASE.plusHours(1), null, null);
        // 배포 전부터 대기 중이라 요청 시각이 없는 행: 건수에만 들어간다.
        InterviewSession waitingWithoutRequestedAt = session(applicant, InterviewSessionStatus.WAITING_FOR_QUESTION,
                null, null, null);

        // 스크립트 저장 대기: 이전 단계의 질문 요청 시각이 더 이르지만 저장 요청 시각 컬럼만 본다.
        InterviewSession transcriptPending = session(applicant, InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED,
                BASE.minusDays(3), BASE.plusHours(2), null);
        InterviewSession reportPending = session(applicant, InterviewSessionStatus.REPORT_REQUESTED,
                BASE.minusDays(3), BASE.minusDays(2), BASE.plusHours(3));

        // 대기 상태가 아닌 행: 요청 시각이 있어도 집계에서 빠진다.
        InterviewSession completed = session(applicant, InterviewSessionStatus.REPORT_COMPLETED,
                BASE.minusDays(5), BASE.minusDays(5), BASE.minusDays(5));

        interviewSessionRepository.saveAll(List.of(
                waitingOld, waitingNew, waitingWithoutRequestedAt, transcriptPending, reportPending, completed));

        // when & then
        assertThat(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .isEqualTo(3);
        assertThat(interviewSessionRepository.findOldestQuestionRequestedAt(InterviewSessionStatus.WAITING_FOR_QUESTION))
                .hasValue(BASE);

        assertThat(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED))
                .isEqualTo(1);
        assertThat(interviewSessionRepository.findOldestTranscriptSaveRequestedAt(
                InterviewSessionStatus.TRANSCRIPT_SAVE_REQUESTED))
                .hasValue(BASE.plusHours(2));

        assertThat(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.REPORT_REQUESTED))
                .isEqualTo(1);
        assertThat(interviewSessionRepository.findOldestReportRequestedAt(InterviewSessionStatus.REPORT_REQUESTED))
                .hasValue(BASE.plusHours(3));
    }

    @Test
    @DisplayName("면접 대기 집계: 대기 세션이 없으면 건수는 0이고 최장 대기 시각은 없다")
    void interviewPendingAggregation_ReturnsZeroAndEmpty_WhenNothingPending() {
        // given
        // (저장된 세션 없음)

        // when & then
        assertThat(interviewSessionRepository.countBySessionStatus(InterviewSessionStatus.REPORT_REQUESTED))
                .isZero();
        assertThat(interviewSessionRepository.findOldestReportRequestedAt(InterviewSessionStatus.REPORT_REQUESTED))
                .isEmpty();
    }

    private InterviewSession session(
            Applicant applicant,
            InterviewSessionStatus status,
            LocalDateTime questionRequestedAt,
            LocalDateTime transcriptSaveRequestedAt,
            LocalDateTime reportRequestedAt
    ) {
        return InterviewSession.builder()
                .applicant(applicant)
                .quarter("2026Q4")
                .sessionStatus(status)
                .questionRequestedAt(questionRequestedAt)
                .transcriptSaveRequestedAt(transcriptSaveRequestedAt)
                .reportRequestedAt(reportRequestedAt)
                .build();
    }
}

package com.weiver.jobposting.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.company.type.*;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.type.JdAnalysisStatus;
import com.weiver.jobposting.type.JobPostingStatus;
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
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JobPostingRepositoryTest.JpaAuditingTestConfig.class)
class JobPostingRepositoryTest {

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
    private JobPostingRepository jobPostingRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Test
    @DisplayName("벌크 업데이트 엣지케이스: 기한이 지난 ACTIVE 상태의 공고만 CLOSED로 변경되어야 한다.")
    void closeExpiredJobPostings_EdgeCases() {
        // given
        LocalDate today = LocalDate.now();

        Company dummyCompany = Company.builder()
                .loginId("testCompany123")
                .password("encodedPassword")
                .companyType(CompanyType.STARTUP)
                .employeeNum(50)
                .companyCeoName("홍길동")
                .companyName("테스트회사")
                .foundedYear(LocalDate.of(2020, 1, 1))
                .avgSale(100)
                .address("경기도 안산시 상록구 한양대학로 55")
                .cultureDescription("자율적이고 수평적인 문화입니다.")
                .directionDescription("글로벌 서비스 진출을 목표로 합니다.")
                .workPace(WorkPace.FAST_EXECUTION)
                .decisionMaking(DecisionMaking.TEAM_CONSENSUS)
                .roleDefinition(RoleDefinition.CLEAR_RESPONSIBILITY)
                .operationStyle(OperationStyle.STABILITY_ORIENTED)
                .build();

        companyRepository.save(dummyCompany);

        // CLOSED로 변경되어야 함
        JobPosting targetExpiredJob = JobPosting.builder()
                .company(dummyCompany)
                .title("백엔드 개발자 채용 (업데이트 대상)")
                .jobCategory("IT")
                .detailedJob("Spring Boot 백엔드 개발")
                .deadline(today.minusDays(1))
                .status(JobPostingStatus.ACTIVE)
                .build();

        // ACTIVE 유지
        JobPosting boundaryTodayJob = JobPosting.builder()
                .company(dummyCompany)
                .title("프론트엔드 개발자 채용 (오늘 마감)")
                .jobCategory("IT")
                .detailedJob("React 프론트엔드 개발")
                .deadline(today)
                .status(JobPostingStatus.ACTIVE)
                .build();

        // 마감일은 지났지만, 임시 저장인 공고
        JobPosting expiredDraftJob = JobPosting.builder()
                .company(dummyCompany)
                .title("기획자 채용 (작성중 방치)")
                .jobCategory("기획")
                .detailedJob("서비스 기획")
                .deadline(today.minusDays(5))
                .status(JobPostingStatus.DRAFT)
                .build();

        // 이미 CLOSED 된 공고 -> CLOSED 유지되어야 함 (불필요한 업데이트 방지)
        JobPosting alreadyClosedJob = JobPosting.builder()
                .company(dummyCompany)
                .title("디자이너 채용 (이미 마감됨)")
                .jobCategory("디자인")
                .detailedJob("UI/UX 디자인")
                .deadline(today.minusDays(10))
                .status(JobPostingStatus.CLOSED)
                .build();

        jobPostingRepository.saveAll(List.of(targetExpiredJob, boundaryTodayJob, expiredDraftJob, alreadyClosedJob));

        // when
        int updatedCount = jobPostingRepository.closeExpiredJobPostings(today);

        // then: 검증
        assertThat(updatedCount).isEqualTo(1);

        JobPosting updatedTarget = jobPostingRepository.findById(targetExpiredJob.getJdId()).orElseThrow();
        JobPosting updatedBoundary = jobPostingRepository.findById(boundaryTodayJob.getJdId()).orElseThrow();
        JobPosting updatedDraft = jobPostingRepository.findById(expiredDraftJob.getJdId()).orElseThrow();
        JobPosting updatedClosed = jobPostingRepository.findById(alreadyClosedJob.getJdId()).orElseThrow();

        assertThat(updatedTarget.getStatus()).isEqualTo(JobPostingStatus.CLOSED);
        assertThat(updatedBoundary.getStatus()).isEqualTo(JobPostingStatus.ACTIVE);
        assertThat(updatedDraft.getStatus()).isEqualTo(JobPostingStatus.DRAFT);
        assertThat(updatedClosed.getStatus()).isEqualTo(JobPostingStatus.CLOSED);
    }
    @Test
    @DisplayName("JD 분석 대기 집계: REQUESTED 공고만 세고, 최장 대기 시각은 요청 시각이 있는 행 중 가장 이른 값이다")
    void jdAnalysisPendingAggregation_CountsRequestedAndFindsOldestIgnoringNull() {
        // given
        Company company = companyRepository.save(company());
        OffsetDateTime oldest = OffsetDateTime.of(2026, 11, 1, 9, 0, 0, 0, ZoneOffset.UTC);
        OffsetDateTime newer = OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC);

        jobPostingRepository.saveAll(List.of(
                jobPosting(company, JdAnalysisStatus.REQUESTED, newer),
                jobPosting(company, JdAnalysisStatus.REQUESTED, oldest),
                // 배포 전부터 대기 중이라 요청 시각이 없는 행: 건수에만 들어간다.
                jobPosting(company, JdAnalysisStatus.REQUESTED, null),
                // 대기 상태가 아닌 행: 요청 시각이 더 이르더라도 집계에서 빠진다.
                jobPosting(company, JdAnalysisStatus.COMPLETED, oldest.minusDays(1)),
                jobPosting(company, JdAnalysisStatus.PENDING, null)
        ));

        // when
        long count = jobPostingRepository.countByJdAnalysisStatus(JdAnalysisStatus.REQUESTED);
        Optional<OffsetDateTime> oldestRequestedAt =
                jobPostingRepository.findOldestJdAnalysisRequestedAt(JdAnalysisStatus.REQUESTED);

        // then
        assertThat(count).isEqualTo(3);
        assertThat(oldestRequestedAt).hasValueSatisfying(
                requestedAt -> assertThat(requestedAt).isAtSameInstantAs(oldest));
    }

    @Test
    @DisplayName("JD 분석 대기 집계: 대기 행의 요청 시각이 모두 비어 있으면 최장 대기 시각은 없다")
    void jdAnalysisPendingAggregation_ReturnsEmptyOldest_WhenAllRequestedAtAreNull() {
        // given
        Company company = companyRepository.save(company());
        jobPostingRepository.save(jobPosting(company, JdAnalysisStatus.REQUESTED, null));

        // when
        long count = jobPostingRepository.countByJdAnalysisStatus(JdAnalysisStatus.REQUESTED);
        Optional<OffsetDateTime> oldestRequestedAt =
                jobPostingRepository.findOldestJdAnalysisRequestedAt(JdAnalysisStatus.REQUESTED);

        // then
        assertThat(count).isEqualTo(1);
        assertThat(oldestRequestedAt).isEmpty();
    }

    private Company company() {
        return Company.builder()
                .loginId("pendingCompany")
                .password("encodedPassword")
                .companyType(CompanyType.STARTUP)
                .employeeNum(10)
                .companyCeoName("홍길동")
                .companyName("대기집계회사")
                .foundedYear(LocalDate.of(2020, 1, 1))
                .avgSale(100)
                .address("서울시")
                .cultureDescription("문화")
                .directionDescription("방향")
                .workPace(WorkPace.FAST_EXECUTION)
                .decisionMaking(DecisionMaking.TEAM_CONSENSUS)
                .roleDefinition(RoleDefinition.CLEAR_RESPONSIBILITY)
                .operationStyle(OperationStyle.STABILITY_ORIENTED)
                .build();
    }

    private JobPosting jobPosting(Company company, JdAnalysisStatus analysisStatus, OffsetDateTime requestedAt) {
        return JobPosting.builder()
                .company(company)
                .title("백엔드 개발자")
                .jobCategory("IT")
                .detailedJob("백엔드")
                .deadline(LocalDate.now().plusDays(7))
                .status(JobPostingStatus.ACTIVE)
                .jdAnalysisStatus(analysisStatus)
                .jdAnalysisRequestedAt(requestedAt)
                .build();
    }
}
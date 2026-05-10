package com.weiver.jobposting.repository;

import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.company.domain.Company;
import com.weiver.company.repository.CompanyRepository;
import com.weiver.company.type.*;
import com.weiver.jobposting.domain.JobPosting;
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
import java.util.List;

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
}
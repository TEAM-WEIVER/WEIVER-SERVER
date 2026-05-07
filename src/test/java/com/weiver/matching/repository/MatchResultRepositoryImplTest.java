package com.weiver.matching.repository;

import com.querydsl.core.Tuple;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.weiver.analysis.domain.CultureReport;
import com.weiver.analysis.domain.TechnicalSkillReport;
import com.weiver.analysis.type.CulturefitStyle;
import com.weiver.applicant.domain.Applicant;
import com.weiver.applicant.domain.WorkExperience;
import com.weiver.applicant.type.EmploymentType;
import com.weiver.company.domain.Company;
import com.weiver.company.type.*;
import com.weiver.jobposting.domain.JobPosting;
import com.weiver.jobposting.type.JobPostingStatus;
import com.weiver.matching.domain.MatchResult;
import com.weiver.matching.dto.request.ApplicantSearchCondition;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.test.util.ReflectionTestUtils;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static com.weiver.matching.domain.QMatchResult.matchResult;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({MatchResultRepositoryTest.QueryDslTestConfig.class, MatchResultRepositoryImpl.class})
class MatchResultRepositoryTest {

    @TestConfiguration
    @EnableJpaAuditing
    static class QueryDslTestConfig {
        @Bean
        public JPAQueryFactory jpaQueryFactory(EntityManager em) {
            return new JPAQueryFactory(em);
        }
    }

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @Autowired
    private MatchResultRepositoryCustom matchResultRepositoryCustom;

    @Autowired
    private EntityManager em;

    private JobPosting testJobPosting;
    private JobPosting otherJobPosting;

    @BeforeEach
    void setUp() {
        // 회사 세팅
        Company dummyCompany = Company.builder()
                .loginId("testCompany123")
                .password("encodedPassword")
                .companyType(CompanyType.STARTUP)
                .employeeNum(50)
                .companyCeoName("김대표")
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
        em.persist(dummyCompany);

        // 공고 세팅
        testJobPosting = JobPosting.builder()
                .company(dummyCompany)
                .title("백엔드 개발자 채용 (테스트용)")
                .jobCategory("IT/개발")
                .detailedJob("Spring Boot 백엔드 개발")
                .deadline(LocalDate.now().plusDays(10))
                .status(JobPostingStatus.ACTIVE)
                .build();
        em.persist(testJobPosting);

        // 지원자 1 세팅
        Applicant app1 = Applicant.builder()
                .publicId("pub-1")
                .name("이현우")
                .email("lee@test.com")
                .password("password123!")
                .phoneNumber("010-1234-5678")
                .build();
        em.persist(app1);

        MatchResult mr1 = MatchResult.builder()
                .jobPosting(testJobPosting)
                .applicant(app1)
                .skillScore(95f)
                .build();
        ReflectionTestUtils.setField(mr1, "createTime", LocalDateTime.now());
        em.persist(mr1);

        em.persist(CultureReport.builder().applicant(app1).culturefitStyles(CulturefitStyle.INCLUSIVE_INNOVATOR).build());
        em.persist(TechnicalSkillReport.builder().applicant(app1).skillTags(List.of("Java", "Spring Boot", "Redis")).build());

        em.persist(WorkExperience.builder()
                .applicant(app1)
                .companyName("A 스타트업")
                .employmentType(EmploymentType.INTERN)
                .isRecognized(false)
                .position("백엔드 주니어")
                .startDate(LocalDate.of(2022, 1, 1))
                .endDate(LocalDate.of(2023, 12, 31))
                .build());

        em.persist(WorkExperience.builder()
                .applicant(app1)
                .companyName("B 중견기업")
                .position("백엔드 시니어")
                .employmentType(EmploymentType.CONTRACT)
                .isRecognized(false)
                .startDate(LocalDate.of(2024, 1, 1))
                .endDate(LocalDate.of(2025, 12, 31))
                .build());

        // 지원자 2 세팅
        Applicant app2 = Applicant.builder()
                .publicId("pub-2")
                .name("홍길동")
                .email("hong@test.com")
                .password("password123!")
                .phoneNumber("010-1111-2222")
                .build();
        em.persist(app2);

        MatchResult mr2 = MatchResult.builder()
                .jobPosting(testJobPosting)
                .applicant(app2)
                .skillScore(80f)
                .build();
        ReflectionTestUtils.setField(mr2, "createTime", LocalDateTime.now().minusDays(1));
        em.persist(mr2);

        em.persist(CultureReport.builder().applicant(app2).culturefitStyles(CulturefitStyle.STRATEGIC_GUARDIAN).build());
        em.persist(TechnicalSkillReport.builder().applicant(app2).skillTags(List.of("React", "TypeScript")).build());

        // 지원자 3 세팅
        otherJobPosting = JobPosting.builder()
                .company(dummyCompany)
                .title("프론트엔드 개발자 채용 (노이즈 데이터)")
                .jobCategory("IT/개발")
                .detailedJob("React 프론트엔드 개발")
                .deadline(LocalDate.now().plusDays(5))
                .status(JobPostingStatus.ACTIVE)
                .build();
        em.persist(otherJobPosting);

        Applicant app3 = Applicant.builder()
                .publicId("pub-3")
                .name("김철수")
                .email("kim@test.com")
                .password("password123!")
                .phoneNumber("010-3333-4444")
                .build();
        em.persist(app3);

        MatchResult mr3 = MatchResult.builder()
                .jobPosting(otherJobPosting)
                .applicant(app3)
                .skillScore(90f)
                .build();
        ReflectionTestUtils.setField(mr3, "createTime", LocalDateTime.now());
        em.persist(mr3);

        em.flush();
        em.clear();
    }

    @Test
    @DisplayName("조건 없음: 해당 공고의 모든 지원자가 점수 내림차순으로 조회된다.")
    void searchApplicants_NoCondition_ReturnsAllForJd() {
        ApplicantSearchCondition condition = ApplicantSearchCondition.builder()
                .jdId(testJobPosting.getJdId())
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<Tuple> result = matchResultRepositoryCustom.searchApplicantsTuple(condition, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);

        Tuple firstTuple = result.getContent().get(0);
        MatchResult firstMr = firstTuple.get(matchResult);
        assertThat(firstMr.getApplicant().getName()).isEqualTo("이현우");
    }

    @Test
    @DisplayName("엣지케이스(이름/점수): 키워드 부분 일치와 최소 점수 필터링이 정상 동작한다.")
    void searchApplicants_WithKeywordAndMinScore() {
        ApplicantSearchCondition condition = ApplicantSearchCondition.builder()
                .jdId(testJobPosting.getJdId())
                .keyword("현우")
                .skillScoreMin(90)
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<Tuple> result = matchResultRepositoryCustom.searchApplicantsTuple(condition, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).get(matchResult).getApplicant().getName()).isEqualTo("이현우");
    }

    @Test
    @DisplayName("엣지케이스(JSONB): 기술 스택 다중 선택 시 AND 조건으로 모두 포함된 지원자만 조회된다.")
    void searchApplicants_WithTechStacks_AND_Condition() {
        ApplicantSearchCondition condition = ApplicantSearchCondition.builder()
                .jdId(testJobPosting.getJdId())
                .techStacks(List.of("Java", "Spring Boot"))
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<Tuple> result = matchResultRepositoryCustom.searchApplicantsTuple(condition, pageable);

        assertThat(result.getTotalElements()).isEqualTo(1);
        assertThat(result.getContent().get(0).get(matchResult).getApplicant().getName()).isEqualTo("이현우");
    }

    @Test
    @DisplayName("엣지케이스(서브쿼리): 경력이 2개면 가장 최신 직무를, 경력이 없으면 null을 반환한다.")
    void searchApplicants_SubQuery_LatestPosition() {
        ApplicantSearchCondition condition = ApplicantSearchCondition.builder()
                .jdId(testJobPosting.getJdId())
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<Tuple> result = matchResultRepositoryCustom.searchApplicantsTuple(condition, pageable);

        List<Tuple> content = result.getContent();

        String position1 = content.get(0).get(3, String.class);
        assertThat(position1).isEqualTo("백엔드 시니어");

        String position2 = content.get(1).get(3, String.class);
        assertThat(position2).isNull();
    }

    @Test
    @DisplayName("엣지케이스(Enum): 잘못된 CultureStyle 문자열이 들어오면 무시하고 전체를 반환한다.")
    void searchApplicants_InvalidCultureStyle_Ignored() {
        ApplicantSearchCondition condition = ApplicantSearchCondition.builder()
                .jdId(testJobPosting.getJdId())
                .cultureStyle("UNKNOWN_STYLE")
                .build();
        Pageable pageable = PageRequest.of(0, 10);

        Page<Tuple> result = matchResultRepositoryCustom.searchApplicantsTuple(condition, pageable);

        assertThat(result.getTotalElements()).isEqualTo(2);
    }
}
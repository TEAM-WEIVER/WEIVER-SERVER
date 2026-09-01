---
name: persistence-conventions
description: WEIVER 서버의 영속성 규칙 — 단일 PostgreSQL DataSource와 Hibernate ddl-auto(Flyway 미사용), JPA 엔티티 표준 애너테이션 세트(@Getter/@NoArgsConstructor(PROTECTED)/@Builder/@AllArgsConstructor(PRIVATE))와 BaseTimeEntity 감사, Long IDENTITY PK + 별도 UUID public_id, @Enumerated(STRING), jsonb 매핑(@JdbcTypeCode), 실제 @ManyToOne/@OneToOne LAZY 연관관계, Spring Data 파생 쿼리·JPQL·QueryDSL 3파일 커스텀 리포지토리, 서비스 계층 @Transactional 경계, 스키마 변경 주의점을 정의한다. 엔티티·리포지토리·쿼리·트랜잭션을 만들거나 수정할 때 사용한다. 계층 배치는 architecture-conventions, 명명·예외는 global-conventions, 서비스·DTO·테스트는 backend-conventions, Redis는 redis-conventions를 따른다.
---

# persistence-conventions (WEIVER)

PostgreSQL 15(**단일 DataSource**, HikariCP) · Spring Data JPA(Hibernate 6) · QueryDSL 5.1 · Spring Batch 미사용.
스키마는 **Hibernate `ddl-auto`** 로 관리한다(Flyway 미사용, §0). 즉 `domain/`의 **JPA 엔티티가 스키마의 정본**이다.

패키지는 `com.weiver.<context>.{domain, repository}` 다 → global-conventions §1

---

## 0. DB 인프라

- **단일 PostgreSQL**(`jdbc:postgresql://.../weiver_db`). 다중 DataSource가 아니므로
  `@Transactional`에 `transactionManager` 이름을 지정하지 않는다(§5).
- 스키마 관리: `spring.jpa.hibernate.ddl-auto` — **local=`create-drop`, prod=`update`, 기본=`validate`**.
  공통 JPA 옵션: `open-in-view: false`, `default_batch_fetch_size: 100`.
- **Flyway는 미사용이다.** `build.gradle`에 의존성만 있고 `src/main/resources/db/migration/`가 없다.
  마이그레이션 SQL(`V1__...`)을 새로 만들지 말 것 — 이 프로젝트는 ddl-auto로 스키마를 파생한다.
- 로컬 인프라는 `docker-compose.yml`(postgres 15 / redis 7 / rabbitmq 4.2).

---

## 1. 도메인 = JPA 엔티티

이 프로젝트는 **도메인 모델과 JPA 엔티티를 분리하지 않는다.** `domain/`의 클래스가 곧 `@Entity`이며
상태 전이 편의 메서드를 가진다(빈약하지 않은 모델) → backend-conventions §4. 별도 매핑 클래스·Mapper를
두지 않는다.

> **MapStruct는 미사용이다.** `build.gradle`에 선언만 있고 `@Mapper`를 쓰는 코드가 없다.
> DTO↔엔티티 변환은 **수작업**(엔티티의 `updateXxx()` 메서드 + DTO의 `from`/`toEntity` 팩토리)으로 한다.

---

## 2. JPA 엔티티 (`domain/`)

### 2.1 표준 애너테이션 세트 (모든 엔티티 고정)

```java
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)   // JPA 전용 기본 생성자
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)    // Builder 전용
@Entity
@Table(name = "applicants")                          // 복수 snake_case
public class Applicant extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "applicant_id")
    private Long applicantId;

    /** 외부 노출용 공개 식별자 — 내부 PK를 숨긴다 */
    @Builder.Default
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private String publicId = UUID.randomUUID().toString();

    @Column(name = "phone_number")
    private String phoneNumber;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private ApplicantStatus status = ApplicantStatus.ACTIVE;
}
```

**규칙**
- **4종 세트 고정**: `@Getter` + `@NoArgsConstructor(access = PROTECTED)` + `@Builder`
  + `@AllArgsConstructor(access = PRIVATE)`. **`@Setter`·`@Data`·`@EqualsAndHashCode`·`@ToString` 금지.**
- 모든 엔티티는 **`BaseTimeEntity`를 상속**한다(§2.5).
- 테이블명은 `@Table(name = ...)`으로 명시(복수 snake_case). 컬럼명은 `@Column(name = "snake_case")`,
  필드는 camelCase. 긴 텍스트는 `columnDefinition = "TEXT"`.
- 상태 변경은 **의미 있는 메서드**(`updateInfo`, `withdraw`, `markJdAnalysisCompleted`)로만. setter 금지.
- 기본값은 `@Builder.Default`로 준다.

### 2.2 식별자 — 내부 PK + 공개 UUID

- **내부 PK**: `Long` + `GenerationType.IDENTITY`(PostgreSQL auto-increment). primitive `long` 금지.
  PK 컬럼명은 도메인별(`applicant_id`, `jd_id`, `interview_id`, `portfolio_id`).
- **공개 식별자**: 외부(URL·API·이벤트)에는 내부 PK 대신 **UUID `public_id`**(String) 또는
  `interview_session_id`(UUID)를 노출한다. `@Builder.Default UUID.randomUUID()`,
  `nullable=false, unique=true, updatable=false`. 조회도 `findByPublicId(...)`로 한다.

### 2.3 enum · jsonb

```java
@Enumerated(EnumType.STRING)             // ORDINAL 금지
@Builder.Default
private JobPostingStatus status = JobPostingStatus.ACTIVE;

@JdbcTypeCode(SqlTypes.JSON)             // Hibernate 6 네이티브 jsonb
@Builder.Default
@Column(name = "transcript", nullable = false, columnDefinition = "jsonb")
private List<InterviewTurnDTO> transcript = new ArrayList<>();
```

- enum 필드는 **항상 `@Enumerated(EnumType.STRING)`**. enum 타입은 컨텍스트의 `type/` 패키지.
- 리스트/DTO를 컬럼에 넣을 때는 **`@JdbcTypeCode(SqlTypes.JSON)` + `columnDefinition = "jsonb"`**
  (별도 컨버터 없이). `List<String>`이나 DTO 리스트를 그대로 매핑한다.

### 2.4 연관관계 — 실제 JPA 매핑 (LAZY)

이 프로젝트는 **실제 객체 참조 매핑**을 쓴다(FK ID 컬럼만 두는 방식이 아니다). **전부 `FetchType.LAZY`.**

```java
@ToString.Exclude
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "applicant_id", nullable = false)
private Applicant applicant;

@OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
@JoinColumn(name = "applicant_id", nullable = false)
@ToString.Exclude
private Applicant applicant;
```

- `@ManyToOne`(예: InterviewSession→Applicant, JobPosting→Company)와 `@OneToOne` 다수.
- **`@OneToMany`·`@ManyToMany`는 쓰지 않는다.** 항상 소유(단방향 `@ManyToOne`/`@OneToOne`) 쪽에서만
  참조한다. 역방향 컬렉션이 필요하면 리포지토리 조회(`findAllByApplicant`)로 가져온다.
- 연관 필드에는 순환/지연로딩 문제 방지를 위해 **`@ToString.Exclude`** 를 붙인다
  (엔티티에 `@ToString`을 안 붙이는 것이 원칙이지만, 상속·롬복 조합에서의 안전장치).
- N+1은 리포지토리에서 `@EntityGraph`/`join fetch`로 제어한다(§3).

### 2.5 감사(Auditing) — `BaseTimeEntity`

```java
@Getter
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseTimeEntity {          // global/common/BaseTimeEntity
    @CreatedDate
    @Column(updatable = false, nullable = false)
    private LocalDateTime createTime;           // 컬럼: create_time

    @LastModifiedDate
    private LocalDateTime updateTime;            // 컬럼: update_time
}
```

- 필드명은 **`createTime`/`updateTime`**(관례적 `createdAt`/`updatedAt`이 아니다). 새 엔티티도 이 이름을 따른다.
- 활성화는 `global/config/JpaAuditingConfig`(`@EnableJpaAuditing`). **작성자 감사(`AuditorAware`)는 없다** — 시간만.
- `BaseEntity`(PK만 가진 상위 클래스)는 없다. 모든 엔티티가 `BaseTimeEntity`를 상속한다.

---

## 3. 리포지토리 (`repository/`)

### 3.1 Spring Data 파생 쿼리 (기본)

```java
@Repository
public interface EducationRepository extends JpaRepository<Education, Long> {
    List<Education> findAllByApplicant(Applicant applicant);
    boolean existsByApplicant(Applicant applicant);
    Optional<Applicant> findByPublicId(String publicId);
}
```

- `JpaRepository<Entity, Long>`을 상속한다. 단건 `Optional<T>`, 다건 `List<T>`/`Page<T>`, 존재확인 `boolean existsBy...`.
- **연관 프로퍼티 탐색은 언더스코어**: `findByJobPosting_JdIdAndApplicant_PublicId(...)`.
- N+1 방지는 `@EntityGraph(attributePaths = {...})`.

### 3.2 JPQL — 제한적으로

`@Query`는 파생 쿼리로 표현하기 어려운 경우에만. 텍스트 블록 + `@Param` 명시.

```java
@Query("""
    select ea from EssayAnswer ea
    join fetch ea.essayQuestion eq
    where ea.applicant = :applicant
    order by eq.sequence asc
    """)
List<EssayAnswer> findAllByApplicantWithQuestionOrderBySequence(@Param("applicant") Applicant applicant);

@Modifying(clearAutomatically = true)
@Query("UPDATE JobPosting j SET j.status = 'CLOSED' WHERE j.status = 'ACTIVE' AND j.deadline < :now")
int closeExpiredJobPostings(@Param("now") LocalDate now);
```

- 컬렉션 `join fetch`와 페이징을 **함께 쓰지 않는다**(메모리 페이징). 필요하면 count 쿼리를 분리한다.
- 벌크 수정은 `@Modifying(clearAutomatically = true)`.

### 3.3 QueryDSL 커스텀 — 3파일 패턴

동적 검색·복잡 조회는 QueryDSL. **표준 3파일**을 따른다.

```java
// 1) Spring Data 인터페이스가 Custom을 상속
public interface MatchResultRepository extends JpaRepository<MatchResult, Long>, MatchResultRepositoryCustom { }

// 2) 커스텀 인터페이스
public interface MatchResultRepositoryCustom { Page<MatchResult> search(SearchCond cond, Pageable pageable); }

// 3) 구현
@Repository
@RequiredArgsConstructor
public class MatchResultRepositoryImpl implements MatchResultRepositoryCustom {
    private final JPAQueryFactory queryFactory;      // QueryDslConfig 빈
    // static import QMatchResult.matchResult
}
```

- `JPAQueryFactory`는 `global/config/QueryDslConfig`의 `@Bean`. 구현체는 `@Repository @RequiredArgsConstructor`로 주입.
- 동적 조건은 **`BooleanExpression`을 반환하는 private 메서드**로 만들고, 조건이 없으면 `null`을 반환해
  `where(...)` 가변인자에서 자동 무시되게 한다.
- 페이징은 `PageableExecutionUtils.getPage(content, pageable, countQuery::fetchOne)` + 별도 count 쿼리.
- jsonb 배열 스캔 등은 `Expressions.booleanTemplate("CAST({0} AS string) like {1}", ...)`.
- Q클래스는 `build/generated/querydsl`에 생성된다(`build.gradle` 설정). `clean` 시 함께 삭제된다.

---

## 4. 리포지토리 테스트

`@DataJpaTest @AutoConfigureTestDatabase(replace = NONE)` + Testcontainers PostgreSQL + `@ServiceConnection`.
QueryDSL 구현체 테스트는 `@TestConfiguration`으로 `JPAQueryFactory` 빈 + `@EnableJpaAuditing`을 주입한다.

```java
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(JpaAuditingTestConfig.class)
class JobPostingRepositoryTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine");

    @TestConfiguration
    @EnableJpaAuditing
    static class JpaAuditingTestConfig {
        @Bean JPAQueryFactory jpaQueryFactory(EntityManager em) { return new JPAQueryFactory(em); }
    }
}
```

- **`@ServiceConnection`** 방식(`@DynamicPropertySource` 안 씀). 컨테이너는 `static @Container`. **Docker 데몬 필요.**
- 인메모리 DB가 아니라 실제 PostgreSQL 컨테이너(`postgres:15-alpine`)로 검증한다.
- 테스트 데이터는 엔티티 `@Builder`로 인라인 생성, 감사 필드 세팅은 `ReflectionTestUtils`.
- 나머지 상세는 backend-conventions §7.

---

## 5. 트랜잭션 경계

- **`@Transactional`은 서비스 계층에만.** 컨트롤러·리포지토리·엔티티·이벤트 핸들러(서비스 위임 전)에는 붙이지 않는다.
- **단일 DataSource이므로 `transactionManager` 이름을 지정하지 않는다.**
- 정책(→ backend-conventions §3):
  - 쓰기 위주 서비스: 클래스 `@Transactional` + 조회 메서드 `@Transactional(readOnly = true)`.
  - 조회 위주 서비스: 클래스 `@Transactional(readOnly = true)` + 쓰기 메서드 `@Transactional`.
- **트랜잭션 안에서 외부 I/O(S3·이메일·RabbitMQ 발행)를 하지 않는다.** 이벤트는 **커밋 후**
  `publishAfterCommit`으로 발행한다 → architecture-conventions §5.4.
- `private` 메서드·self-invocation에는 프록시가 걸리지 않는다.

---

## 6. 스키마 변경 주의 (ddl-auto)

`prod`는 `ddl-auto=update`라 **추가만** 반영하고 삭제·타입 축소·이름 변경은 반영하지 않는다.

| 하려는 것 | `update`가 해주는가 | 대응 |
|---|---|---|
| 컬럼/테이블 추가 | ✅ | 그대로. `nullable` 기본값 주의 |
| 컬럼 삭제 / 타입 축소 | ❌ | 팀과 합의 후 수동 DDL |
| 테이블명·컬럼명 변경 | ❌ (**새로 만든다**) | 원칙적으로 하지 않는다 |

- **테이블명·컬럼명을 바꾸지 않는 것이 제1원칙.** 이름을 바꾸면 `update`가 새 테이블/컬럼을 만들고 데이터가 분리된다.
- 클래스명을 바꿔도 `@Table(name=...)`/`@Column(name=...)`이 명시돼 있으면 스키마 영향이 없다.
- `local`은 `create-drop`이라 재기동마다 초기화된다. 스키마 정합성은 기본 프로파일 `validate`가 부팅 시 잡는다.

---

## 7. 체크리스트

**엔티티**
- [ ] `domain/`에 있고 4종 애너테이션 세트 + `BaseTimeEntity` 상속인가 (`@Setter`/`@Data` 없음)
- [ ] `@Table(name=...)`·`@Column(name=...)`으로 이름을 명시했는가
- [ ] PK가 `Long` IDENTITY이고 외부 노출용 `public_id`(UUID)를 별도로 두었는가
- [ ] enum이 `@Enumerated(EnumType.STRING)`, 리스트/DTO는 `@JdbcTypeCode(SqlTypes.JSON)` jsonb인가
- [ ] 연관관계가 LAZY이고 `@ToString.Exclude`가 있으며 `@OneToMany`/`@ManyToMany`를 쓰지 않았는가
- [ ] 상태 변경을 setter가 아니라 편의 메서드로 하는가

**리포지토리**
- [ ] `JpaRepository<E, Long>` 파생 쿼리 위주, 단건은 `Optional`인가
- [ ] N+1을 `@EntityGraph`/`join fetch`로 제어하고 컬렉션 fetch+페이징을 함께 쓰지 않는가
- [ ] QueryDSL이 3파일 패턴(Repository extends Custom / Custom / Impl)이고 동적 조건이 `null` 무시 방식인가
- [ ] Flyway 마이그레이션(`V1__`)을 새로 만들지 않았는가

**트랜잭션**
- [ ] `@Transactional`이 서비스 계층에만 있고 조회에 `readOnly = true`인가
- [ ] 트랜잭션 안에서 S3/이메일/이벤트 발행을 하지 않는가 (이벤트는 커밋 후)
- [ ] 테이블명·컬럼명을 바꾸지 않았는가

**테스트**
- [ ] 리포지토리 테스트가 `@DataJpaTest`+Testcontainers PostgreSQL+`@ServiceConnection`인가
- [ ] `.\gradlew.bat test` 통과

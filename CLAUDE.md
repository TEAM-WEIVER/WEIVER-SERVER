# CLAUDE.md

## 프로젝트 개요

WEIVER는 **"검증된 인재와 기업을 연결하는"** AI 면접·채용 매칭 플랫폼의 백엔드 서버다.
구직자 프로필·자기소개서·포트폴리오를 AI로 분석하고, 채용 공고(JD)를 분석해 기업↔구직자를
매칭하며, AI 면접(WebSocket)을 진행한다. AI 처리는 별도 AI 서버가 담당하고 Spring 서버와는
**RabbitMQ 이벤트**로만 연동한다.

Backend-only Spring Boot API 서버. **Java 21 · Spring Boot 3.5.13 · Gradle(Wrapper)**.

핵심 인프라:
- **PostgreSQL 15** (단일 DataSource, Hibernate `ddl-auto`로 스키마 관리 — Flyway 미사용)
- **Redis 7** (JWT 토큰·이메일 인증 등 임시 키-값 저장. 범용 캐시로는 미사용)
- **RabbitMQ 4.2** (도메인 이벤트, AI 서버 연동)
- QueryDSL · AWS S3/SQS · WebSocket(STOMP) · JWT(Spring Security) · Sentry · springdoc(Swagger)
- 로컬 인프라는 `docker-compose.yml`(postgres/redis/rabbitmq)로 띄운다.

## 아키텍처 · 패키지 구조

**전통적 계층형(layered) 아키텍처**다. DDD 헥사고날·포트/어댑터가 **아니다** — `domain` 패키지가
곧 JPA 엔티티이며 도메인 모델과 엔티티를 분리하지 않는다.

```
com.weiver.<context>.<layer>     업무 컨텍스트(모듈)
  <layer> = controller / service / domain(JPA 엔티티) / dto(request,response)
            / repository / event(dto,handler) / type(enum)

com.weiver.global.<area>         컨텍스트에 속하지 않는 공통 기반
  <area> = common(ApiResponse,BaseTimeEntity) / exception / event(RabbitMQ 인프라)
           / security(JWT,principal,websocket) / auth / s3 / email / logging / config
```

- 컨텍스트: `auth`, `applicant`, `company`, `jobposting`, `essay`, `portfolio`, `interview`,
  `analysis`, `matching`, `notification`, `dashboard`.
- 패키지는 **전부 소문자**이며 `com.weiver` 바로 아래에 컨텍스트가 온다(`com.weiver.domain.*` 묶음 없음).
- 의존 방향: `controller → service → repository → domain`. `@Transactional`은 **서비스 계층에만** 붙인다.
- 컨텍스트 간 통신은 두 갈래다 — **동기**(다른 컨텍스트의 service/repository 직접 주입, 느슨한 격리)와
  **비동기**(AI·장기 파이프라인은 RabbitMQ 도메인 이벤트). 자세한 것은 `architecture-conventions`.

## 규약 파일 (Convention Files)

작업하려는 범위의 규칙 파일을 먼저 읽는다. 여러 영역에 걸치면 `architecture-conventions`도 함께 읽는다.

| 범위 | 규칙 파일 |
| --- | --- |
| 계층 구조·의존 방향, 컨텍스트 통신, RabbitMQ 이벤트 아키텍처(발행/수신/핸들러/봉투/DLQ), WebSocket 면접 플로우, 컨텍스트·기능 추가 | `.claude/skills/architecture-conventions/SKILL.md` |
| 컨트롤러·서비스·도메인 엔티티·DTO 작성 워크플로우, 로직 배치 기준, 계층별 테스트 전략 | `.claude/skills/backend-conventions/SKILL.md` |
| 패키지·클래스 명명, 예외(ErrorCode/BusinessException/GlobalExceptionHandler), 응답 봉투(ApiResponse), 보안(JWT/security), 로깅·Sentry, S3·이메일 | `.claude/skills/global-conventions/SKILL.md` |
| JPA 엔티티·BaseTimeEntity, Repository(파생 쿼리/JPQL/QueryDSL), 트랜잭션 경계, ddl-auto 스키마 변경 | `.claude/skills/persistence-conventions/SKILL.md` |
| Redis 키-값 저장소(JWT 토큰·인증 토큰), 키 네임스페이스·TTL·해시 저장·원자적 회전 | `.claude/skills/redis-conventions/SKILL.md` |

## 에이전트 라우팅

| 역할 | 파일 | 권한 |
| --- | --- | --- |
| backend-developer | `.claude/agents/backend-developer.md` | 배정 범위의 유일한 작성자. 계층형 구조에 맞춰 기능 구현 |
| code-reviewer | `.claude/agents/code-reviewer.md` | 읽기 전용. 변경 파일을 아키텍처·프로젝트 규칙과 대조해 리뷰. 코드 수정 안 함 |

## AI 에이전트 핵심 규칙

사용자가 명시적으로 요청하지 않은 코드 변경이나 작업을 하지 않는다. 사용자 지시를 엄격히 따르고
선제적 수정·추가를 하지 않는다.

## 일반 규칙

- 항상 한국어로 응답한다.
- 사용자가 요청한 변경과 무관한 파일을 수정하지 않는다.
- 새 권한, 외부 시스템, 데이터베이스 스키마 변경처럼 영향이 큰 결정은 먼저 확인받는다.
- 사용자 승인 없이 파일을 삭제하거나 파괴적 Git 명령을 실행하지 않는다.
- `git push`·`git commit`은 사용자가 명시적으로 요청할 때만 실행한다.
- **멱등성·재시도·이벤트 실패(DLQ) 재처리 정책은 아직 확정되지 않았다. 새 정책을 지어내지 않는다.**

## 검증 (Verification)

테스트는 Windows에서 `.\gradlew.bat test`, 그 외 `./gradlew test`로 실행한다. 실패를 숨기거나 우회하지
말고 명확히 보고한다.

- **대부분의 테스트는 DB 없이 돈다.** 서비스는 Mockito 단위 테스트, 컨트롤러는 `@WebMvcTest` +
  `@MockitoBean`(보안 필터는 `addFilters=false`로 끄고 `SecurityContext` 수동 주입), 이벤트 핸들러·
  WebSocket 컨트롤러는 순수 단위 테스트다.
- **리포지토리 테스트만** Testcontainers로 실제 PostgreSQL(`postgres:15-alpine`)을 띄운다
  (`@DataJpaTest` + `@ServiceConnection`) — 이때 **Docker 데몬이 떠 있어야 한다**.
- 실패 경로 테스트는 HTTP 상태뿐 아니라 응답의 `errorCode`(=`ErrorCode` 이름)까지 단언한다.
- 공통 `IntegrationTestSupport` 베이스 클래스, `src/test/resources`, `test` 프로파일, 전용 Fixture
  클래스는 **없다**. 테스트 데이터는 엔티티 `@Builder`로 인라인 생성한다.

이 프로젝트의 버전 주의사항:

- **Testcontainers는 `testcontainers-bom` 1.20.4**로 버전을 고정한다(`build.gradle`의
  `testcontainersVersion`). 사용 모듈은 `postgresql`, `junit-jupiter`, `rabbitmq`이며,
  `PostgreSQLContainer<?>`는 `org.testcontainers.containers` 패키지에 있다. (`rabbitmq` 모듈은 선언만
  돼 있고 현재 테스트 코드에서는 미사용이다.)
- **Flyway와 MapStruct는 `build.gradle`에 의존성이 있으나 실제로는 미사용**이다. Flyway 마이그레이션
  (`V1__...`)을 새로 만들지 말고 스키마는 ddl-auto로 파생한다. DTO↔엔티티 변환은 MapStruct가 아니라
  수작업(`from`/`toEntity` 팩토리 + 엔티티 편의 메서드)으로 한다.
- **관리되는 Jackson 버전은 Jackson 2(`com.fasterxml.jackson`)** 다. `com.fasterxml.jackson.databind.ObjectMapper`를 쓴다.

# AGENTS.md

이 저장소에서 일하는 모든 AI 에이전트(Claude Code, Codex 등)가 따르는 공통 규칙이다.
자세한 기술 규칙은 아래 매뉴얼 지도의 문서를 필요할 때 읽는다. 이 파일에는 짧고 안정적인 규칙만 둔다.

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

## 매뉴얼 지도

작업하려는 범위의 문서를 먼저 읽는다. 여러 영역에 걸치면 `architecture-conventions`도 함께 읽는다.
같은 내용을 여기에 다시 쓰지 않고 링크로 연결한다.

| 범위 | 문서 |
| --- | --- |
| 계층 구조·의존 방향, 컨텍스트 통신, RabbitMQ 이벤트 아키텍처(발행/수신/핸들러/봉투/DLQ), WebSocket 면접 플로우, 컨텍스트·기능 추가 | [architecture-conventions](.claude/skills/architecture-conventions/SKILL.md) |
| 컨트롤러·서비스·도메인 엔티티·DTO 작성 워크플로우, 로직 배치 기준, 계층별 테스트 전략 | [backend-conventions](.claude/skills/backend-conventions/SKILL.md) |
| 패키지·클래스 명명, 예외(ErrorCode/BusinessException/GlobalExceptionHandler), 응답 봉투(ApiResponse), 보안(JWT/security), 로깅·Sentry, S3·이메일 | [global-conventions](.claude/skills/global-conventions/SKILL.md) |
| JPA 엔티티·BaseTimeEntity, Repository(파생 쿼리/JPQL/QueryDSL), 트랜잭션 경계, ddl-auto 스키마 변경 | [persistence-conventions](.claude/skills/persistence-conventions/SKILL.md) |
| Redis 키-값 저장소(JWT 토큰·인증 토큰), 키 네임스페이스·TTL·해시 저장·원자적 회전 | [redis-conventions](.claude/skills/redis-conventions/SKILL.md) |
| 구현 판단·코드 이해 보고서 템플릿과 작성 기준 | [implementation-report](.claude/templates/implementation-report.md) |
| 브랜치·커밋 명명, 실행 방법 | [README](README.md) |

## 공통 작업 흐름

1. **시작** — 담당 Issue, 현재 브랜치와 기준 커밋을 확인한다. 기본 브랜치(`main`)에 직접 커밋하지 않고
   `<type>/#<이슈번호>-<간단설명>` 브랜치에서 작업한다. 관련 명세·계획이 있으면 먼저 읽는다.
2. **조사** — 매뉴얼 지도에서 범위에 맞는 문서만 읽고, 수정 대상과 그 호출부, 기존 테스트를 확인한다.
   저장소 전체를 읽지 않는다.
3. **구현 전 보고** — 주요 구현 결정이 있으면 보고서의 "구현 전" 부분(문제·대안·선택 근거)을 쓴다.
   결정의 출처를 AI 제안 / 사용자 결정 / 승인된 범위 안의 구현 판단 / 명세 기재(승인 기록 없이 명세에만 적힌 결정)로 구분해 적는다. 사소한 변경은 짧은 보고로 충분하다.
4. **구현** — 요청 범위 안에서 최소한으로 바꾼다. 구현 중 판단이 바뀌면 보고서에 이유와 함께 남긴다.
5. **검증** — 관련 테스트부터 좁게 돌리고 범위를 넓힌다. 결과는 아래 완료 기준의 상태로 기록한다.
6. **구현 후 보고와 리뷰** — 보고서의 "구현 후" 부분(호출 흐름·핵심 코드·검증 결과)을 쓰고, 리뷰어가
   보고서의 설명을 실제 코드와 검증 증거에 대조한다.
7. **PR** — 팀 PR 템플릿을 따른다. 보고서의 핵심(결정·근거·판단 변경·호출 흐름)은 PR 본문 `구현 보고` 절에,
   검증 결과는 `검증 결과` 절에 적는다. 보고서 파일은 커밋하지 않는다. 병합은 사람이 한다.

## 승인 기준 (먼저 묻는다)

아래는 사용자 확인 없이 하지 않는다. (요청하지 않은 작업과 무관한 파일 수정은 확인 대상이 아니라 금지다 — 일반 규칙 참고)

- 새 권한, 외부 시스템, 데이터베이스 스키마 변경(엔티티·테이블·컬럼 변경 포함), 공개 API 계약, 의존성,
  인증·보안 정책 변경처럼 영향이 큰 결정.
- 파일 삭제, 파괴적 Git 명령.
- `git commit`·`git push`. 사용자가 명시적으로 요청하거나 작업을 시작할 때 허용한 범위에서만 한다.
- 병합, 배포, 저장소 설정 변경.
- **멱등성·재시도·이벤트 실패(DLQ) 재처리 정책은 아직 확정되지 않았다. 새 정책을 지어내지 않는다.**

## 중단 기준 (멈추고 보고한다)

- 같은 원인으로 수정이 두 번 실패했을 때.
- 필요한 검증을 실행할 수 없을 때(Docker·비밀값·외부 서비스 등 환경 차단).
- 규칙끼리 충돌하거나, 문서와 실제 코드가 다를 때.
- 요청 범위 밖의 변경이 필요해 보일 때.
- 실패가 원래 있던 실패인지 이번 변경의 회귀인지 구분할 수 없을 때.

## 완료 기준

- 완료는 세 가지로 나눠 보고한다: **구현 완료**(코드 변경 끝), **검증 완료**(필수 검증 실행·통과),
  **설명 전달 완료**(보고서·PR에 판단 근거와 결과 기록).
- 검증 결과는 **통과 / 실패 / 미실행 / 환경 차단**으로 적고, 실행한 명령과 결과를 함께 남긴다.
  **미실행·환경 차단 항목을 통과로 기록하지 않는다.**
- 실패를 숨기거나 테스트를 약화·삭제해 통과시키지 않는다.

## 검증 (Verification)

테스트는 Windows에서 `.\gradlew.bat test`, 그 외 `./gradlew test`로 실행한다. 실패를 숨기거나 우회하지
말고 명확히 보고한다.

- **대부분의 테스트는 DB 없이 돈다.** 서비스는 Mockito 단위 테스트, 컨트롤러는 `@WebMvcTest` +
  `@MockitoBean`(보안 필터는 `addFilters=false`로 끄고 `SecurityContext` 수동 주입), 이벤트 핸들러·
  WebSocket 컨트롤러는 순수 단위 테스트다.
- **리포지토리 테스트만** Testcontainers로 실제 PostgreSQL(`postgres:15-alpine`)을 띄운다
  (`@DataJpaTest` + `@ServiceConnection`) — 이때 **Docker 데몬이 떠 있어야 한다**.
- **`WeiverBackendApplicationTests`(`@SpringBootTest` contextLoads)는 주의한다.** `local` 프로필로
  `localhost:5432/weiver_db`에 붙고 `ddl-auto: create-drop`이라, 로컬 DB가 떠 있으면 스키마를 지울 수 있다.
  로컬 DB를 쓰는 중이면 이 테스트를 빼고 돌리고, 미실행으로 기록한다.
- **문서 검사:** 하네스 문서를 바꿨으면 `bash .github/scripts/check-docs.sh`로 필수 문서와 로컬 링크를
  검사한다. 링크 대상은 Git이 추적하는 파일이어야 한다.
- **PR CI:** `main` 대상 PR마다 `.github/workflows/pr-check.yml`이 문서 검사와 `./gradlew test`(contextLoads 제외)를 돌린다.
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

## 일반 규칙

- 사용자가 명시적으로 요청하지 않은 코드 변경이나 작업을 하지 않는다. 사용자 지시를 엄격히 따르고
  선제적 수정·추가를 하지 않는다.
- 사용자가 요청한 변경과 무관한 파일을 수정하지 않는다.
- 항상 한국어로 응답한다. 지침·보고서·리뷰도 한국어로 쓴다.
- 비밀값을 읽어 출력하거나 커밋하지 않는다. `.env`는 Git 무시 대상이다. `application-local.yaml`·
  `application-prod.yaml`은 **Git이 추적하는 파일**이므로 비밀값을 직접 쓰지 않고 `${ENV}` 플레이스홀더만 쓴다.

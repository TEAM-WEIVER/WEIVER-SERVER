---
name: architecture-conventions
description: WEIVER 서버의 계층형(layered) 아키텍처를 정의한다. com.weiver.<context>.<layer> 패키지 구조, 컨텍스트(모듈) 목록과 계층별 역할, 의존 방향, 컨텍스트 간 통신(다른 컨텍스트 서비스·리포지토리 직접 주입 vs RabbitMQ 도메인 이벤트), RabbitMQ 이벤트 아키텍처(토폴로지·발행·수신·디스패치·핸들러·봉투·DLQ), STOMP WebSocket 면접 플로우, 새 컨텍스트·기능 추가 절차를 담는다. 새 클래스를 어느 계층에 둘지, 컨텍스트를 동기로 부를지 이벤트로 부를지, 이벤트 핸들러/발행을 만들지 정할 때 사용한다. 명명·예외·응답 봉투·보안·로깅은 global-conventions, 서비스·컨트롤러·DTO·테스트는 backend-conventions, JPA 엔티티·리포지토리·QueryDSL·트랜잭션은 persistence-conventions를 따른다.
---

# architecture-conventions (WEIVER)

WEIVER는 "검증된 인재와 기업을 연결하는" **AI 면접 · 채용 매칭 플랫폼**의 백엔드 서버다.
아키텍처는 **전통적 계층형(layered)** 이다. DDD 헥사고날·포트/어댑터가 **아니다** —
`domain` 패키지가 곧 JPA 엔티티이고, 도메인 모델과 엔티티를 분리하지 않는다.

Java 21 · Spring Boot 3.5.13 · Gradle · PostgreSQL 15 · Redis 7 · RabbitMQ 4.2.

---

## 1. 패키지 구조

패키지는 **전부 소문자**다. 루트는 `com.weiver`이며 두 갈래다 — 업무 컨텍스트와 공통 기반(`global`).

```
com.weiver.<context>.<layer>     ← 업무 컨텍스트(모듈)
com.weiver.global.<area>         ← 컨텍스트에 속하지 않는 공통 기반
```

> ⚠️ `com.weiver.domain.<context>` 같은 **중간 묶음 디렉터리는 없다.** 컨텍스트는 `com.weiver`
> 바로 아래에 온다(`com.weiver.jobposting`, `com.weiver.matching`). 여기서 `domain`은
> **컨텍스트 안의 JPA 엔티티 계층**을 가리키는 하위 패키지 이름일 뿐이다(§3).

### 1.1 컨텍스트(모듈) 목록

| 컨텍스트 | 책임 | 컨트롤러 | AI 이벤트 |
|---|---|---|---|
| `auth` | 인증·토큰 발급·이메일 인증 (지원자/기업) | ✅ 3개 | — |
| `applicant` | 구직자 프로필·학력·수상·자격증·경력 | ✅ | 프로필 동기화 트리거 |
| `company` | 기업 정보 | ✅ | — |
| `jobposting` | 기업 채용 공고(JD) | ✅ | JD 분석 |
| `essay` | 자기소개서(문항/답변) | ✅ | — |
| `portfolio` | 구직자 포트폴리오 | ✅ | 프로필 변경 트리거 |
| `interview` | AI 면접 세션 (WebSocket + 이벤트 상태기계) | ✅(WebSocket) | 질문 생성·스크립트 저장·리포트 |
| `analysis` | 지원자 AI 분석 리포트 저장/조회 | ✕ (이벤트 수신) | 지원자 분석 결과 |
| `matching` | 기업↔구직자 매칭 결과·리포트 | ✅ 2개 | 매칭 |
| `notification` | 기업 알림 | ✕ (dashboard 경유) | — |
| `dashboard` | 기업 대시보드 집계(여러 컨텍스트 조합) | ✅ | — |

- 컨트롤러가 없는 컨텍스트(`analysis`, `notification`)는 **자체 HTTP API를 노출하지 않는다.**
  이벤트로 데이터를 받거나, 다른 컨텍스트 서비스가 조합해 노출한다.
- `global`은 컨텍스트가 아니다(§3.2).

---

## 2. 계층과 의존 방향

의존은 항상 **바깥 → 안쪽** 단방향이다.

```
controller ─▶ service ─▶ repository ─▶ domain(JPA 엔티티)
    │            │
    │            └─▶ event(*EventService: 이벤트 발행)
    ▼
  dto(request/response)

event/handler ─▶ service / repository / domain   (이벤트 수신 → 처리)
```

| 계층 | 패키지 | 역할 | 어노테이션 |
|---|---|---|---|
| presentation | `controller/` | REST 컨트롤러. HTTP 입출력·인증 principal·검증만 | `@RestController` |
| business | `service/` | 트랜잭션 경계. 비즈니스 로직·오케스트레이션 | `@Service @Transactional` |
| persistence | `repository/` | Spring Data JPA · QueryDSL | `@Repository`(인터페이스) |
| domain | `domain/` | **JPA 엔티티** + 상태 전이 편의 메서드 | `@Entity` |
| DTO | `dto/request`, `dto/response` | 요청/응답 계약 | `record` |
| 이벤트 | `event/`(+`dto`, `handler`) | 컨텍스트 간 비동기 통신(§4·§5) | — |
| enum | `type/` | 컨텍스트 전용 상태/타입 enum | — |

**규칙**
- **DTO ↔ 엔티티 변환은 서비스에서** 한다. 컨트롤러는 DTO를 그대로 서비스에 넘긴다.
- 엔티티는 **HTTP 응답으로 직접 노출하지 않는다.** 응답 DTO(`from(entity)`)로 변환한다.
- `@Transactional`은 **서비스 계층에만** 붙인다. 컨트롤러·리포지토리·엔티티에 붙이지 않는다 → persistence-conventions §5

### 2.1 컨텍스트 격리는 느슨하다 (현실 인정)

이 프로젝트의 컨텍스트 경계는 **강한 격리가 아니다.** 조회·조합이 필요하면 서비스가
**다른 컨텍스트의 service·repository·domain을 직접 주입**해서 쓴다(§4.1). 새 코드도 이 관행을
따르되, 아래를 지킨다.

- 순환 의존을 만들지 않는다(A 서비스 ↔ B 서비스 상호 주입 금지).
- **AI가 관여하는 장기 파이프라인은 직접 호출이 아니라 이벤트로** 연결한다(§4.2).

### 2.2 `global` — 공통 기반

`global`은 컨텍스트가 아니라 모든 컨텍스트가 공유하는 기술·공통 기반이다.

```
com.weiver.global.
├── common/       ApiResponse, BaseTimeEntity, UserRole, YearMonthAttributeConverter
├── exception/    ErrorCode, BusinessException, ErrorResponse, ErrorDetail, handler(GlobalExceptionHandler)
├── event/        RabbitMQ 이벤트 인프라 (config/publisher/consumer/dto/validation/util/exception) → §5
├── security/     JWT, 쿠키, CSRF, principal, websocket 인증 → global-conventions §4
├── auth/         AuthenticatedPrincipalResolver (현재 인증 주체 조회)
├── s3/           S3Service
├── email/        EmailSender / ResendEmailSender / LoggingEmailSender
├── logging/      ServiceLoggingAspect, HttpLoggingFilter, TraceIdGenerator → global-conventions §5
└── config/       SecurityConfig, CorsConfig, RedisConfig, WebSocketConfig, SwaggerConfig,
                  QueryDslConfig, JpaAuditingConfig, WhiteListConfig
```

---

## 3. 컨텍스트 내부 표준 배치

```
com.weiver.<context>/
├── controller/          <Xxx>Controller               (@RestController, @RequestMapping("/api/..."))
├── service/             <Xxx>Service                   (@Service @Transactional)
├── domain/              <Xxx>                          (@Entity, BaseTimeEntity 상속)
├── dto/
│   ├── request/         <Xxx>RequestDTO / <Xxx>UpdateDTO  (record + Bean Validation)
│   └── response/        <Xxx>ResponseDTO / <Xxx>PageResponseDTO (record + from(entity))
├── repository/          <Xxx>Repository (Spring Data), <Xxx>RepositoryCustom / <Xxx>RepositoryImpl (QueryDSL)
├── event/
│   ├── <Xxx>EventService                              (이벤트 발행)
│   ├── dto/             <Xxx>RequestedData / <Xxx>CompletedData (record, snake_case @JsonProperty)
│   └── handler/         <Xxx>Handler                   (implements DomainEventHandler)
└── type/                <Xxx>Status / <Xxx>Type        (enum)
```

**없는 것은 필요가 없어서 없는 것이다.** 관례를 맞추려고 빈 패키지를 만들지 않는다.
- `analysis`·`notification`에는 `controller`가 없다(자체 API 미노출).
- 인터페이스+구현 분리(`Service`/`ServiceImpl`)는 **예외적**이다. 현재 `auth`만 그렇다 → backend-conventions §3

---

## 4. 컨텍스트 간 통신

두 가지 방식이 공존하며 **목적이 다르다.**

### 4.1 동기 — 다른 컨텍스트 서비스/리포지토리 직접 주입

조회·조합, 즉시 정합성이 필요한 경우. 생성자 주입으로 바로 부른다.

```java
// applicant/service/ApplicantService.java — 다른 컨텍스트 repository를 직접 주입
@Service
@Transactional
@RequiredArgsConstructor
public class ApplicantService {
    private final ApplicantRepository applicantRepository;
    private final EssayAnswerRepository essayAnswerRepository;   // essay 컨텍스트
    private final PortfolioRepository portfolioRepository;       // portfolio 컨텍스트
    ...
}
```

- `dashboard`는 `JobPostingService`·`NotificationService`를 주입해 화면용 데이터를 조합한다.
- `interview`의 `InterviewFlowService`는 `analysis`·`applicant`의 repository를 주입해 면접 질문
  payload를 만든다.

### 4.2 비동기 — RabbitMQ 도메인 이벤트

**AI 서버 연동과 장기 파이프라인은 전부 이벤트**다. AI 서버는 별도 프로세스이며,
Spring 서버와 **RabbitMQ 토픽 익스체인지로만** 주고받는다(HTTP/REST 호출 없음 — §7).

이벤트로 연결하는 기준:
- 처리가 **AI 서버(외부 프로세스)** 를 거친다 → 반드시 이벤트
- 처리가 오래 걸리거나 **여러 단계 파이프라인**이다(프로필 동기화 → 분석 → 매칭, 면접 플로우)
- 발신 컨텍스트가 수신 결과를 **즉시 기다릴 필요가 없다**

동기 직접 호출로 두는 기준:
- 같은 요청 안에서 **즉시 조회·조합**하고 응답에 써야 한다
- 트랜잭션 안에서 정합성이 필요하다

> 핸들러가 다음 단계를 이어가는 **릴레이 구조**가 있다: 예) `APPLICANT_PROFILE_SYNC_COMPLETED`
> 를 받은 핸들러가 성공 시 `applicantAnalysisEventService.publishApplicantAnalysisRequested(...)`
> 를 호출해 `APPLICANT_ANALYSIS_REQUESTED`를 발행한다. 즉 핸들러가 다음 컨텍스트의
> `*EventService`(동기 주입)를 불러 **다음 이벤트를 발행**한다.

---

## 5. 이벤트 아키텍처 (RabbitMQ)

인프라는 `com.weiver.global.event`에 있다. Spring `ApplicationEventPublisher`/`@EventListener`는
**쓰지 않는다.** 커스텀 `DomainEventPublisher`가 `RabbitTemplate`을 직접 감싸고, 수신은 단일
`@RabbitListener`가 받아 in-app 디스패처가 `EventType`별 핸들러로 라우팅한다.

### 5.1 토폴로지 (`global/event/config`)

- **토픽 익스체인지** `domain.events` + DLX `domain.events.dlx`.
- 큐 2개, 각각 DLQ 연결:
  - `spring.domain.events.queue` ← Spring 서버가 소비. 바인딩 `#.completed`, `#.generated`, `interview.transcript.saved` (**AI → Spring 결과**)
  - `ai.domain.events.queue` ← AI 서버가 소비. 바인딩 `#.requested`, `applicant.profile.changed` (**Spring → AI 요청**)
- 이름값은 `weiver.rabbitmq.*` 프로퍼티(`RabbitMqProperties` record, `@NotBlank`).
- 리스너: `MANUAL` ack, `prefetch=10`, `defaultRequeueRejected=false`, 애플리케이션 재시도 **비활성**(`retry.enabled=false`).

### 5.2 이벤트 봉투 — `EventEnvelope<T>` (record)

| 필드(@JsonProperty) | 의미 |
|---|---|
| `event_id` | UUID (`EventIds.newEventId()`) |
| `event_type` | `EventType` enum |
| `correlation_id` | 요청↔결과 상관관계 (요청은 null) |
| `occurred_at` | `OffsetDateTime` |
| `version` | 스키마 버전. 현재 `"1.0"` (`CURRENT_VERSION`) |
| `data` | payload |

```java
EventEnvelope.request(EventType.APPLICANT_ANALYSIS_REQUESTED, data, EventIds.newEventId()); // correlationId 없음
EventEnvelope.result (EventType.MATCHING_COMPLETED, data, eventId, correlationId);           // correlationId 포함
```

### 5.3 `EventType` — 요청/결과 쌍

`dto/EventType.java`. 라우팅 키는 `EventRoutingKeys.from()` = `name().toLowerCase().replace("_", ".")`
(예: `MATCHING_COMPLETED` → `matching.completed`).

```
APPLICANT_PROFILE_CHANGED / APPLICANT_PROFILE_SYNC_COMPLETED   프로필 동기화
JD_ANALYSIS_REQUESTED     / JD_ANALYSIS_COMPLETED              JD 분석
APPLICANT_ANALYSIS_REQUESTED / APPLICANT_ANALYSIS_COMPLETED    지원자 분석
MATCHING_REQUESTED        / MATCHING_COMPLETED                 매칭
INTERVIEW_QUESTION_REQUESTED / INTERVIEW_QUESTION_GENERATED    면접 질문 생성
INTERVIEW_TRANSCRIPT_SAVE_REQUESTED / INTERVIEW_TRANSCRIPT_SAVED  면접 스크립트 저장
INTERVIEW_REPORT_REQUESTED / INTERVIEW_REPORT_COMPLETED        면접 리포트 생성
```

### 5.4 발행 — `*EventService` → `DomainEventPublisher`

컨텍스트마다 `event/<Xxx>EventService`가 봉투를 만들어 발행한다. 발행 컴포넌트는
`DomainEventPublisher` **하나뿐**이며 `RabbitTemplate` + publisher confirm(10초 타임아웃)을 감싼다.

```java
EventEnvelope<ApplicantAnalysisRequestedData> envelope = EventEnvelope.request(
        EventType.APPLICANT_ANALYSIS_REQUESTED,
        new ApplicantAnalysisRequestedData(applicantId),
        EventIds.newEventId());
domainEventPublisher.publish(envelope);
```

> ⚠️ **발행은 트랜잭션 커밋 후에.** DB 변경이 커밋되기 전에 이벤트가 나가면 AI 서버가 아직 없는
> 상태를 읽는다. `publishAfterCommit(...)` 헬퍼(`TransactionSynchronizationManager`로 `afterCommit`
> 등록, 트랜잭션이 없으면 즉시 발행)를 쓴다. `ApplicantProfileEventService`·`InterviewFlowService`가
> 이 방식이다. **새 발행부는 이 패턴을 기본으로 따른다**(일부 기존 서비스는 트랜잭션 내 직접
> 발행이라 정합성 위험이 있다 — 확장하지 말 것).

### 5.5 수신 — 리스너 → 디스패처 → 핸들러

개별 핸들러는 `@RabbitListener`가 **아니다.** 단일 리스너가 받아 in-app 디스패치한다.

```java
// consumer/DomainEventListener.java
@RabbitListener(queues = "${weiver.rabbitmq.spring-queue}")
public void onMessage(Message message, Channel channel) throws IOException {
    EventEnvelope<JsonNode> envelope = objectMapper.readValue(message.getBody(), new TypeReference<>() {});
    validator.validate(envelope);          // 봉투 필수 필드 + version=="1.0"
    dispatcher.dispatch(envelope);         // EventType → DomainEventHandler
    channel.basicAck(deliveryTag, false);
    // 실패 → basicNack(deliveryTag, false, false) → DLQ (requeue 안 함)
}
```

핸들러는 `DomainEventHandler`를 구현한 `@Component`. 디스패처가 부팅 시
`List<DomainEventHandler>`를 `EnumMap<EventType, …>`으로 등록한다(중복 등록 시 예외).

```java
public interface DomainEventHandler {
    EventType support();
    void handle(EventEnvelope<JsonNode> envelope);
}

@Component
@RequiredArgsConstructor
public class ApplicantAnalysisCompletedHandler implements DomainEventHandler {
    private final ObjectMapper objectMapper;
    private final TechnicalSkillReportRepository technicalSkillReportRepository;
    // ...
    @Override public EventType support() { return EventType.APPLICANT_ANALYSIS_COMPLETED; }

    @Override
    @Transactional
    public void handle(EventEnvelope<JsonNode> envelope) {
        var data = objectMapper.convertValue(envelope.data(), ApplicantAnalysisCompletedData.class);
        validate(data);                    // payload 필수값은 핸들러가 직접 검증(@Valid 미사용)
        // 지원자 기준 upsert (findBy...ifPresentOrElse) — 도메인 상태 기반 멱등 처리
    }
}
```

### 5.6 멱등성 · 재시도 · DLQ

- **멱등성**: 전용 dedup 저장소가 **없다.** 핸들러가 **도메인 상태 기반**으로 멱등을 보장한다
  (`if (status == COMPLETED) return;`, `findBy...().ifPresentOrElse(update, save)`). 새 핸들러도
  같은 방식으로 재수신에 안전하게 만든다.
- **재시도**: AMQP 리스너 재시도 비활성, 애플리케이션 재시도 루프 없음.
- **DLQ**: 실패 시 `basicNack(requeue=false)` → DLX → `*.dlq`. `RetryableEventException`도 현재는
  곧바로 DLQ로 간다. 예외는 `RetryableEventException` / `NonRetryableEventException`
  (하위: `UnsupportedEventTypeException`, `UnsupportedEventVersionException`).

> 멱등성·재시도·DLQ 재처리 정책은 아직 확정되지 않았다. **새 정책을 지어내지 말 것.**

---

## 6. WebSocket 면접 플로우 (interview)

- **STOMP over WebSocket.** 설정 `global/config/WebSocketConfig`: 엔드포인트 `/ws`,`/ws-sockjs`,
  브로커 `/queue`·`/topic`, app prefix `/app`, user prefix `/user`.
- `InterviewWebSocketController`는 `@Controller @Validated`(REST 아님) + 얇은 위임.
  `@MessageMapping("/interviews/start")`, `@MessageMapping("/interviews/{id}/answers")`,
  응답 `@SendToUser("/queue/interviews")`. 인증은 `java.security.Principal.getName()`(=publicId).
- `InterviewFlowService`가 **상태기계 오케스트레이터**다. `InterviewSessionStatus`
  (STARTED → WAITING_FOR_QUESTION → QUESTION_READY → FINISHED → TRANSCRIPT_SAVE_REQUESTED →
  TRANSCRIPT_SAVED → REPORT_REQUESTED → REPORT_COMPLETED)를 전이시키며 각 단계에서 AI 이벤트를
  `publishAfterCommit`으로 발행한다.
- AI 회신 이벤트는 `interview/event/handler/*Handler`가 받아 `InterviewFlowService`의
  `handleQuestionGenerated`/`handleTranscriptSaved`/`handleReportCompleted`로 위임한다.
- 능동 푸시는 `SimpMessagingTemplate.convertAndSendToUser(publicId, "/queue/interviews", ...)`,
  역시 커밋 후 전송.
- WebSocket 인증은 `global/security/websocket/WebSocketAuthChannelInterceptor`(STOMP CONNECT 시
  토큰 검증) → global-conventions §4.

---

## 7. 외부 연동

- **AI 서버 = RabbitMQ 이벤트 전용.** `RestTemplate`/`RestClient`/`FeignClient`/WebClient로 AI를
  호출하는 코드는 없다.
- **WebClient는 이메일(Resend) 전송에만** 쓴다(`global/email`) → global-conventions §7.
- 파일 저장은 S3(`global/s3/S3Service`, public/private 버킷) → global-conventions §6.

---

## 8. 구조를 확장할 때

### 8.1 새 컨텍스트 추가
1. `com.weiver.<context>/` 아래 **필요한 계층만** 만든다(§3). 빈 패키지 금지.
2. 공개 진입점은 `controller`(HTTP) 또는 이벤트 `handler`(비동기)다.
3. 다른 컨텍스트가 조회로 쓸 것은 `service`의 `public` 메서드로 연다.
4. AI 파이프라인이 있으면 `event/`에 `*EventService`(발행)·`dto/*Data`·`handler/*Handler`를 만들고
   §5의 봉투·`EventType`·라우팅 규칙을 따른다.

### 8.2 새 이벤트 추가
1. `EventType`에 **요청/결과 쌍**을 추가한다(라우팅 키 = 소문자·점 표기).
2. 라우팅 키가 어느 큐(`#.requested` → AI, `#.completed`/`#.generated` → Spring)에 바인딩되는지
   확인하고, 필요하면 `RabbitTopologyConfig`에 바인딩을 추가한다.
3. payload는 `event/dto`의 **record**(snake_case `@JsonProperty`)로 만든다.
4. 수신이 필요하면 `DomainEventHandler` 구현체를 추가한다(`support()`가 `EventType` 반환).
5. 발행은 `publishAfterCommit`으로, 수신 핸들러는 **도메인 상태 기반 멱등**으로 만든다.

---

## 9. 아키텍처 리뷰 체크리스트

**계층**
- [ ] 패키지가 `com.weiver.<context>.<layer>` 또는 `com.weiver.global.<area>`인가 (전부 소문자)
- [ ] `@Transactional`이 서비스 계층에만 있는가 (컨트롤러/리포지토리/엔티티에 없음)
- [ ] 컨트롤러가 엔티티를 응답으로 노출하지 않고 `*ResponseDTO`로 변환하는가
- [ ] DTO↔엔티티 변환이 컨트롤러가 아니라 서비스에서 일어나는가

**컨텍스트 통신**
- [ ] AI/장기 파이프라인을 동기 호출이 아니라 이벤트로 연결했는가
- [ ] 서비스 간 순환 주입이 없는가

**이벤트**
- [ ] `EventType`에 요청/결과 쌍을 추가하고 라우팅 키가 큐 바인딩과 맞는가
- [ ] payload가 `event/dto`의 record이고 snake_case `@JsonProperty`인가
- [ ] 발행이 `publishAfterCommit`(커밋 후)인가
- [ ] 핸들러가 `DomainEventHandler`를 구현하고 도메인 상태 기반으로 멱등한가
- [ ] 멱등성/재시도/DLQ 정책을 새로 지어내지 않았는가

**공통**
- [ ] `.\gradlew.bat test` 통과

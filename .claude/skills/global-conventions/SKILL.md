---
name: global-conventions
description: WEIVER 서버의 계층형 구조에서 통용되는 전역 규약 — 소문자 패키지 규칙과 클래스 접미사, 예외 설계(ErrorCode enum이 HttpStatus·기본 메시지 보유, BusinessException, GlobalExceptionHandler, ErrorResponse), 공통 성공 응답 봉투 ApiResponse와 페이징, 보안(JWT·쿠키·CSRF·AuthenticatedPrincipal·WebSocket 인증), 로깅(AOP·MDC traceId·Sentry), 설정 클래스, S3·이메일을 정의한다. 새 클래스 이름을 정하거나, 예외를 던지거나, ErrorCode를 추가하거나, 응답을 감싸거나, 인증·로깅을 다룰 때 사용한다. 계층 배치·이벤트는 architecture-conventions, 서비스·컨트롤러·DTO·테스트는 backend-conventions, JPA·트랜잭션은 persistence-conventions, Redis 토큰은 redis-conventions를 따른다.
---

# global-conventions (WEIVER)

Java 21 · Spring Boot 3.5.13 · Gradle · PostgreSQL 15 · Redis 7 · RabbitMQ 4.2 · AWS S3/SQS · Sentry.
아키텍처는 **계층형(layered)** 이다 → architecture-conventions

---

## 1. 패키지 · 명명

### 1.1 패키지

**전부 소문자.** 다단어도 붙여 쓴다(`jobposting`, `essayanswer` — 언더스코어·캐멀 금지).

```
com.weiver.<context>.<layer>[.<sub>]     ← 컨텍스트 (controller/service/domain/dto/repository/event/type)
com.weiver.global.<area>[.<sub>]         ← 공통 기반 (common/exception/event/security/auth/s3/email/logging/config)
```

> `com.weiver.domain.<context>` 같은 묶음 디렉터리는 **없다.** 컨텍스트는 `com.weiver` 바로 아래다.

### 1.2 클래스 접미사

| 계층/영역 | 접미사 | 예시 |
|---|---|---|
| 컨트롤러 | `...Controller` | `ApplicantController`, `JobPostingController` |
| 서비스 | `...Service` (인터페이스+구현 시 `...ServiceImpl`) | `ApplicantService`, `AuthService`/`AuthServiceImpl` |
| 이벤트 발행 서비스 | `...EventService` | `JobPostingEventService`, `ApplicantAnalysisEventService` |
| 이벤트 핸들러 | `...Handler` | `ApplicantAnalysisCompletedHandler` |
| Spring Data 리포지토리 | `...Repository` | `ApplicantRepository` |
| QueryDSL 커스텀 | `...RepositoryCustom` / `...RepositoryImpl` | `MatchResultRepositoryCustom`/`Impl` |
| JPA 엔티티 (`domain/`) | **접미사 없음** (도메인명 그대로) | `Applicant`, `JobPosting`, `InterviewSession` |
| 상태/타입 enum (`type/`) | `...Status` / `...Type` | `JobPostingStatus`, `JdAnalysisStatus` |
| 도메인 요청 DTO | `...RequestDTO` / `...UpdateDTO` | `JobPostingRequestDTO`, `ApplicantUpdateDTO` |
| 도메인 응답 DTO | `...ResponseDTO` / `...PageResponseDTO` | `JobPostingResponseDTO`, `JobPostingPageResponseDTO` |
| 이벤트 payload DTO (`event/dto`) | `...Data` | `JdAnalysisCompletedData`, `MatchingCompletedData` |
| 스케줄러 | `...Scheduler` | `JobPostingScheduler` |
| 설정 | `...Config` / `...Properties` | `SecurityConfig`, `JwtProperties` |
| 예외 | `...Exception` | `BusinessException`, `RetryableEventException` |
| 필터/Aspect | `...Filter` / `...Aspect` | `JwtAuthenticationFilter`, `ServiceLoggingAspect` |

**규칙**
- **도메인 DTO는 `~DTO`(대문자) 접미사**, `global` 공통 DTO는 **무접미사 의미 이름**
  (`ApiResponse`, `ErrorResponse`, `ErrorDetail`, `EmailSendRequest`). 이 구분을 지킨다.
- JPA 엔티티에 `Entity`/`JpaEntity` 접미사를 붙이지 않는다. `domain/` 위치가 곧 엔티티다.
- 최신 불변 타입은 `record`(응답 DTO, `ErrorDetail`, `AuthenticatedPrincipal`, `PageInfo`,
  이벤트 payload). 봉투류(`ApiResponse`, `ErrorResponse`)는 `@Builder` 클래스다.

### 1.3 API 경로

- 공개 API는 전부 `/api` 하위. 리소스는 **복수-하이픈**(`/api/job-postings`, `/api/applicants`).
- 인증은 `@AuthenticationPrincipal AuthenticatedPrincipal principal`, null이면 `BusinessException(UNAUTHORIZED)`.
- `SecurityConfig`는 세션 `STATELESS`, 화이트리스트(`WhiteListConfig`) 외 `authenticated`(§4).

---

## 2. 공통 응답 봉투

### 2.1 성공 — `ApiResponse<T>` (`global/common`)

```java
@Getter @Builder @AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ApiResponse<T> {
    private String status;   // "success" / "fail"
    private int code;        // HTTP 상태
    private T data;
    private String message;

    public static <T> ApiResponse<T> success(T data);                  // 200
    public static <T> ApiResponse<T> success(String message);          // 데이터 없는 성공(삭제/로그아웃)
    public static <T> ApiResponse<T> created(T data, String message);  // 201
}
```

- 컨트롤러는 `ResponseEntity<ApiResponse<T>>`로 감싼다 → backend-conventions §2.
- **오류 응답은 `ApiResponse`가 아니라 `ErrorResponse`**다(§3).

### 2.2 페이징

`global/common`에 공통 페이징 봉투는 **없다.** 컨텍스트가 자체 정의한다.

```java
public record PageInfo(int pageNumber, int pageSize, long totalElements, int totalPages, boolean isLast) {}
public record JobPostingPageResponseDTO(List<JobPostingsDetails> content, PageInfo pageable) {
    public static JobPostingPageResponseDTO of(Page<?> page, List<JobPostingsDetails> content) { ... }
}
```

Spring Data `Page`를 받아 `of(page, content)`로 변환하는 패턴을 따른다.

---

## 3. 예외 설계

### 3.1 구조

```
global/exception/
├── ErrorCode.java              enum — (code, httpStatus, defaultMessage) 보유 ★
├── BusinessException.java      RuntimeException + ErrorCode
├── ErrorDetail.java            record(field, message) — 필드 검증 오류
├── ErrorResponse.java          응답 바디 (아래)
└── handler/GlobalExceptionHandler.java   @RestControllerAdvice
```

```java
// ErrorCode — HttpStatus와 기본 메시지를 직접 보유한다
@Getter
public enum ErrorCode {
    // AUTH / TOKEN
    UNAUTHORIZED("UNAUTHORIZED", HttpStatus.UNAUTHORIZED, "인증이 필요합니다."),
    TOKEN_EXPIRED("TOKEN_EXPIRED", HttpStatus.UNAUTHORIZED, "액세스 토큰이 만료되었습니다."),
    TOKEN_REUSE_DETECTED("TOKEN_REUSE_DETECTED", HttpStatus.UNAUTHORIZED, "리프레시 토큰 재사용이 감지되었습니다. 다시 로그인해 주세요."),
    // VALIDATION
    VALIDATION_FAILED("VALIDATION_FAILED", HttpStatus.BAD_REQUEST, "입력값 검증에 실패했습니다."),
    // JOB_POSTING
    JOB_POSTING_NOT_FOUND("JOB_POSTING_NOT_FOUND", HttpStatus.NOT_FOUND, "존재하지 않는 공고입니다."),
    // SERVER
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR", HttpStatus.INTERNAL_SERVER_ERROR, "서버 오류가 발생했습니다.");

    private final String code;              // enum 상수명과 동일
    private final HttpStatus httpStatus;
    private final String defaultMessage;
}

// BusinessException — 커스텀 예외는 이것 하나
@Getter
public class BusinessException extends RuntimeException {
    private final ErrorCode code;
    public BusinessException(ErrorCode code)                 { super(code.getDefaultMessage()); this.code = code; }
    public BusinessException(ErrorCode code, String message) { super(message);                  this.code = code; }
}
```

`ErrorResponse`(`@Builder` 클래스, 정적 팩토리 `of(...)`):
`status`(항상 `"error"`), `httpStatus`(int), `errorCode`, `message`, `timestamp`, `path`, `errors`(`List<ErrorDetail>`).

### 3.2 규칙

1. **모든 업무 오류는 `throw new BusinessException(ErrorCode.XXX)`.** 다른 런타임 예외를 직접 던지지 않는다.
2. `ErrorCode`는 **도메인 섹션 주석**(`// AUTH`, `// JOB_POSTING`, `// SERVER` …)으로 묶여 있다.
   새 코드는 해당 섹션에 넣고, `code` 문자열은 enum 상수명과 동일하게 둔다.
3. **`ErrorCode`가 `HttpStatus`를 직접 갖는다.** 별도 매퍼 클래스는 없다 —
   `GlobalExceptionHandler`가 `ex.getCode().getHttpStatus()`로 상태코드를 정한다.
4. **try/catch를 컨트롤러·서비스에 두지 않는다.** `GlobalExceptionHandler`가 처리한다.
5. **클라이언트에 내부 예외 메시지·개인정보·인증정보를 노출하지 않는다.** fallback은 고정 문구
   (`INTERNAL_SERVER_ERROR`)를 반환한다.

### 3.3 `GlobalExceptionHandler`

`@RestControllerAdvice` + `extends ResponseEntityExceptionHandler`.

| 대상 | 로그 | Sentry | 응답 |
|---|---|---|---|
| `BusinessException` | `log.warn("[BusinessException] ...")` | **제외**(의도된 예외) | `code.httpStatus` + `ErrorResponse` |
| `@Valid` 실패(`MethodArgumentNotValid`) | warn | 제외 | `VALIDATION_FAILED` + fieldErrors→`ErrorDetail` |
| JSON 파싱(`HttpMessageNotReadable`) | warn | 제외 | `MALFORMED_JSON` |
| 메서드/미디어타입/파라미터/타입 | warn | 제외 | `METHOD_NOT_ALLOWED` / `UNSUPPORTED_MEDIA_TYPE` / `BIND_FAILED` |
| 그 외 `Exception`(fallback) | `log.error(..., ex)` | **전송** | `INTERNAL_SERVER_ERROR` |

- Spring MVC 표준 예외는 새 핸들러를 만들지 말고 **부모 메서드를 `@Override`** 한다.
- `handleExceptionInternal`에서 5xx는 `log.error(..., ex)`(Sentry), 4xx는 `log.warn`(Sentry 제외).
- 특례: `TOKEN_REUSE_DETECTED`면 refresh 쿠키 만료 `Set-Cookie`를 함께 내려 클라이언트를 강제 로그아웃한다.

---

## 4. 보안 (`global/security`, `global/config/SecurityConfig`)

**JWT 기반, 세션 STATELESS.** 하위: `jwt/`(+`repository/`,`type/`), `cookie/`, `csrf/`, `principal/`,
`handler/`, `websocket/`, `util/`.

- **JWT** (`security/jwt`): `JwtTokenProvider`(HMAC-SHA, 클레임 `subject=publicId`, `role`, `tokenVersion`),
  `JwtAuthenticationFilter`(`OncePerRequestFilter` — 블랙리스트 확인 → `tokenVersion` 비교 → `AuthenticatedPrincipal`
  생성 → SecurityContext 세팅), `BearerTokenResolver`, `JwtProperties`.
  토큰 저장소는 **Redis** 기반이다(`jwt/repository`: `BlacklistTokenRepository`, `RefreshTokenRepository`,
  `TokenVersionRepository`) → redis-conventions.
- **principal**: `AuthenticatedPrincipal`은 **record**(`publicId`, `UserRole role`) implements `Principal`,
  `getName()`이 publicId를 반환한다. **`CustomUserDetails`/`UserDetailsService`는 없다.**
  현재 인증 주체는 `global/auth/AuthenticatedPrincipalResolver`로 서비스에서 조회한다.
- **쿠키**(`cookie/`): `CookieProvider`가 refresh 토큰을 httpOnly 쿠키로 발급/만료(`CookieProperties`).
- **CSRF**(`csrf/`): `CookieCsrfTokenRepository`(httpOnly=false) + `CsrfCookieFilter`. `/ws**`는 CSRF ignore.
- **handler**: `SecurityErrorResponseWriter`가 필터/EntryPoint/AccessDeniedHandler에서 `ErrorResponse`를
  JSON으로 직접 write 해 예외 응답 포맷을 통일한다.
- **`SecurityConfig`**: `PasswordEncoder=BCrypt`, EntryPoint→`UNAUTHORIZED`, AccessDenied→`FORBIDDEN`.
  permitAll: `OPTIONS /**`, swagger, `/actuator/health`, `/api/auth/reissue`·`/api/auth/csrf`,
  `/ws`·`/ws/**`, POST 한정 지원자/기업 인증 경로(이메일 인증·회원가입·로그인). 나머지 `authenticated`.
  화이트리스트는 `config/WhiteListConfig`(정적 메서드)로 분리한다 — **새 permitAll 경로는 여기에 추가**한다.
- **WebSocket 인증**(`websocket/WebSocketAuthChannelInterceptor`): STOMP `CONNECT` 시 `Authorization`
  Bearer 또는 `access_token` 네이티브 헤더에서 토큰을 뽑아 검증하고 `accessor.setUser(principal)`.

> ⚠️ GET/OPTIONS 외 메서드나 새 공개 경로를 추가하면 `SecurityConfig`·`CorsConfig`·`WhiteListConfig`를
> 함께 검토한다. CORS allowedOrigins/allowCredentials는 `CorsConfig`에 있다.

---

## 5. 로깅 (`global/logging`) · 모니터링(Sentry)

- `@Slf4j`(Lombok)를 쓴다. `LoggerFactory` 수동 선언·`System.out`·`printStackTrace()` 금지.
- **문자열 연결(`+`) 금지, `{}` 플레이스홀더** 사용. **비밀값 금지**(토큰, apiKey, DB 접속정보, 전체 URL).
- MDC `traceId`가 로그 패턴(`logback-spring.xml`)에 들어간다. `com.weiver`=DEBUG, root=INFO.

| 구성요소 | 역할 |
|---|---|
| `logging/aspect/ServiceLoggingAspect` | `within(com.weiver..*Service)` `@Around`. 시작/끝 `debug`, `>1000ms`면 `[Slow Service] ... warn` |
| `logging/filter/HttpLoggingFilter` | `@Order(HIGHEST_PRECEDENCE)`. `X-Trace-Id` MDC 세팅, `[HTTP REQUEST]`/`[HTTP RESPONSE]`(2xx/3xx info, 4xx/5xx warn), `finally`에서 `MDC.clear()` |
| `logging/util/TraceIdGenerator` | `UUID` 앞 8자리 |

- **Sentry**: `sentry-spring-boot-starter`. logback 연동으로 **`log.error` = Sentry 이벤트**
  (`minimum-event-level: error`). 그래서 `GlobalExceptionHandler`는 의도된 `BusinessException`/4xx를
  `log.warn`(Sentry 제외), 예측 못한 예외/5xx만 `log.error(..., ex)`(Sentry 전송)로 구분한다 — 이 정책을 지킨다.

---

## 6. S3 (`global/s3`)

- `s3/service/S3Service` 단일 클래스(**인터페이스 없음**). `io.awspring.cloud.s3.S3Template` 사용.
- 버킷 2개(public/private, `@Value`). `publicUpload`/`privateUpload(MultipartFile, dirName)`,
  `getPresignedUrl(fileUrl)`(30분), `deleteFile(fileUrl)`. 키 `<dirName>/<UUID>.<ext>`.
- **확장자 화이트리스트 검증**, 위반 시 `BusinessException(BAD_REQUEST)`, 삭제 실패 `FAIL_DELETE_FILE`.
- 서비스에서 파일 업로드가 필요하면 이 컴포넌트를 주입해 쓴다.

---

## 7. 이메일 (`global/email`)

- `EmailSender` 인터페이스(`void send(EmailSendRequest)`). 구현 2개:
  - `ResendEmailSender` (`@Profile("prod")`) — Resend API를 **WebClient**(`resendWebClient`)로 호출,
    실패 시 `EMAIL_SEND_FAILED`.
  - `LoggingEmailSender` — 비-prod 대체(로그만).
- `email/config`(`ResendConfig`, `ResendProperties`), `email/dto`(`EmailSendRequest` record — `ofText`/`ofHtml`).
- **WebClient는 이메일 전용**이다. `global/config`에 `WebClientConfig`는 없다.

---

## 8. 체크리스트

- [ ] 패키지가 `com.weiver.<context>.<layer>` 또는 `com.weiver.global.<area>`인가 (전부 소문자)
- [ ] 클래스 접미사가 §1.2 카탈로그와 맞는가 (도메인 DTO `~DTO`, 엔티티 무접미사)
- [ ] 오류가 `BusinessException` + `ErrorCode`인가, 새 `ErrorCode`를 해당 도메인 섹션에 넣었는가
- [ ] 성공은 `ApiResponse`, 오류는 `ErrorResponse`로 나뉘는가 (컨트롤러/서비스가 오류를 직접 조립하지 않음)
- [ ] 새 permitAll 경로를 `WhiteListConfig`에 추가하고 CORS/CSRF를 검토했는가
- [ ] `@Slf4j` + `{}` + 비밀값 미노출, 5xx만 `log.error`(Sentry)인가
- [ ] 인증 주체가 `AuthenticatedPrincipal`(record)이고 null 체크가 있는가
- [ ] `.\gradlew.bat test` 통과

# 면접 분석 준비 확인 및 오류 전달 설계 명세

작성일: 2026-10-01 · 버전: v0.1 · 상태: 구현 전 검토용

이 문서의 SDD는 Spec-Driven Development를 뜻한다. 제품 요구사항을 구현 계약과 검증 가능한 동작으로 구체화한다. 문서 작성 완료는 구현 승인·구현 완료·테스트 통과를 의미하지 않는다.

## 1. 문서 체계와 기준

| 문서 | 책임 |
| --- | --- |
| [PRD](../../prd/2026-10-01-interview-analysis-readiness-prd.md) | 문제·범위·FR-1~FR-5·AC-1~AC-12의 정본 |
| 이 설계 명세 | 인터페이스·판단 규칙·예외 범위·상태별 동작 |
| [구현 계획](../plans/2026-10-01-interview-analysis-readiness.md) | T1~T5 작업과 테스트 실행 순서 |
| [검증 명세](2026-10-01-interview-analysis-readiness-validation.md) | 수용 기준 추적, 시나리오, 실행 증거와 완료 판정 |

현재 코드 기준은 `5e5dd5a`다. 구현 시작 시 변경 여부를 다시 확인한다. PRD와 충돌하는 요구가 발견되면 코드로 임의 해석하지 않고 관련 명세를 먼저 함께 수정한다. 요구사항 ID는 유지하고 새 요구는 별도 ID로 추가한다.

## 2. 목표와 불변 조건

분석 준비 여부를 조회와 면접 시작 검증에서 동일하게 판단하고, 면접 요청의 업무 예외를 기존 구독 채널로 전달한다.

- DB 스키마·AI 이벤트 흐름·DLQ 정책을 변경하지 않는다.
- 새 분석 상태·재분석 API·재시도 정책·캐시·외부 의존성을 추가하지 않는다.
- `analysisCompleted`는 저장된 `job/role`의 유효성만 나타낸다. 최신 제출본이나 모든 분석 산출물의 완성을 보장하지 않는다.
- 기존 인증·재응시 제한·서류 작성·프로필 제출 규칙을 유지한다.
- 기존 서버 검증을 유지하고 프론트 버튼 제어에만 의존하지 않는다.
- Java 21, Spring Boot 3.5.13, Jackson 2와 기존 Gradle Wrapper를 사용한다.
- 사용자 요청 전에는 구현·커밋·푸시하지 않는다. 기존 미추적 파일과 무관한 변경을 포함하지 않는다.

## 3. 컴포넌트 책임

아래 Java 경로는 `src/main/java/com/weiver/` 기준이다.

| 파일 | 책임과 변경 |
| --- | --- |
| `analysis/domain/TechnicalSkillReport.java` | 자신의 `job/role`로 완료 여부 계산 |
| `analysis/repository/TechnicalSkillReportRepository.java` | 기존 `findByApplicant(Applicant)` 재사용, 변경 없음 |
| `applicant/service/ApplicantService.java` | 지원자·서류·기술 리포트를 조회해 상태 응답 조합 |
| `applicant/dto/response/ApplicantSubmissionStatusResponseDTO.java` | 마지막 record 컴포넌트로 `boolean analysisCompleted` 추가 |
| `interview/service/InterviewFlowService.java` | 기존 필드 검사를 엔티티 메서드로 교체 |
| `interview/dto/response/InterviewWebSocketMessageResponse.java` | 마지막 record 컴포넌트로 `String errorCode`와 오류 팩토리 추가 |
| `interview/websocket/InterviewWebSocketController.java` | 해당 컨트롤러의 업무 예외 변환·반환 |

`ApplicantService`가 다른 컨텍스트 repository를 직접 주입하는 방식은 기존 동기 조회 규칙을 따른다. 이를 위해 별도 서비스 인터페이스나 이벤트를 만들지 않는다.

## 4. 계약 C1 — 분석 준비 판단

공개 인터페이스:

```java
public boolean isAnalysisCompleted()
```

반환 규칙은 `StringUtils.hasText(job) && StringUtils.hasText(role)`이다. `StringUtils`는 `org.springframework.util.StringUtils`다. 저장 값을 trim하거나 수정하지 않는다.

| 입력 집합 | 결과 |
| --- | --- |
| 리포트 없음 | 호출자가 `false` 또는 기존 업무 예외로 처리 |
| `job` 또는 `role`이 `null`, `""`, `" "`, `"\t\n"` | `false` |
| `job="DEVELOPER"`, `role="BACKEND"` | `true` |
| `job=" DEVELOPER "`, `role=" BACKEND "` | `true`, 원문 유지 |
| 두 값은 유효하고 `skillTags`가 null 또는 빈 목록 | `true` |

`getCompletedAnalysis(Applicant)`는 조회 실패 또는 메서드 반환값이 `false`이면 기존 `BusinessException(ErrorCode.APPLICANT_ANALYSIS_NOT_COMPLETED)`를 던진다. 기존 호출 위치와 트랜잭션을 유지한다.

현재 면접 시작은 세션 `save()` 호출 후 질문 payload를 구성하면서 분석을 검사한다. 따라서 단위 테스트에 `interviewSessionRepository.save()` 미호출을 새 요구로 추가하지 않는다. 분석 미완료 시 질문 요청 이벤트가 발행되지 않는 기존 조건을 검증한다. 순수 Mockito 테스트로 DB 롤백을 검증했다고 주장하지 않는다.

## 5. 계약 C2 — REST 상태 응답

- 경로: `GET /api/applicants/submission-status`
- 서비스: `ApplicantSubmissionStatusResponseDTO getSubmissionStatus(String publicId)` 유지.
- 필드: `data.analysisCompleted`, primitive `boolean`, 필수 반환, null 불가.
- DTO 순서: 기존 여섯 컴포넌트 뒤에 `analysisCompleted`를 추가한다. JSON 소비자는 필드 순서에 의존하지 않는다.
- 조회: 인증된 지원자의 `findByApplicant(applicant)` 결과를 C1으로 판정하고 부재 시 `false`.
- 기존 `@Transactional(readOnly = true)` 유지. 조회 중 저장·이벤트 발행 없음.
- 지원자 부재·인증 실패는 기존 오류 처리 유지. 조회 실패를 `false` 성공 응답으로 숨기지 않는다.

Swagger 문구는 PRD FR-2를 그대로 사용한다. 기존 `submitted`, `syncStatus`, `submittable`, `resumeCompleted`, `essayCompleted`, `portfolioCompleted` 값은 변경하지 않는다.

`syncStatus`는 산식에 포함하지 않는다. 따라서 과거 유효 리포트가 있는 `REQUESTED` 지원자에게도 `analysisCompleted=true`가 가능하다. 이 조합을 최신 분석 완료로 해석하지 않는다. `FAILED`나 미제출 안내와 버튼의 기존 제약은 프론트에서 유지한다.

정상 완료의 관찰 시점은 결과 저장 커밋 이후에 시작한 조회다. 이벤트 수신 로그 시각과 같다고 가정하지 않는다.

## 6. 계약 C3 — WebSocket 업무 오류

팩토리와 핸들러:

```java
public static InterviewWebSocketMessageResponse error(ErrorCode code, String message)

@MessageExceptionHandler(BusinessException.class)
@SendToUser(destinations = "/queue/interviews", broadcast = false)
public InterviewWebSocketMessageResponse handleBusinessException(BusinessException e)
```

| 필드 | 업무 오류 | 기존 정상 메시지 |
| --- | --- | --- |
| `type` | `"ERROR"` | 기존 네 가지 타입 유지 |
| `error_code` | `code.code` 문자열 | `null` |
| `message` | 예외의 사용자 안내 메시지 | 기존 값 유지 |
| `interview_session_id`, `status`, `question_code`, `sequence`, `question` | 모두 `null` | 기존 값 유지 |

`errorCode`에는 `@JsonProperty("error_code")`를 붙인다. 정상 팩토리 네 곳의 생성 인자를 수정하고 기존 null 직렬화 설정을 유지한다. `ErrorCode` 자체나 HTTP 숫자 상태를 JSON에 넣지 않는다.

반환값은 요청 연결의 `/user/queue/interviews` 구독으로 전달되는 STOMP `MESSAGE` 본문이다. 다른 사용자는 물론 같은 사용자의 다른 연결로도 해당 오류를 방송하지 않는다. 이 동작의 프레임 라우팅은 실제 연결로 검증한다. [Spring 공식 문서](https://docs.spring.io/spring-framework/reference/6.2/web/websocket/stomp/user-destination.html)

핸들러는 `startInterview`와 `submitAnswer`의 업무 예외를 함께 처리한다. 같은 연결에서는 시작 또는 답변 요청을 한 번에 하나만 진행한다. 오류에 요청 ID나 면접 세션 ID를 추가하는 계약은 이번 범위에 없다.

CONNECT 인증 실패, 역직렬화·Bean Validation 실패, 연결 단절, 비동기 AI 실패는 C3의 보장 범위가 아니다. STOMP `ERROR` 프레임·연결 종료 처리는 기존 경로를 유지한다. 정상 메시지의 `@SendToUser` 방송 설정도 이번에 변경하지 않는다.

## 7. 계약 C4 — 프론트 상태와 폴링

프론트 저장소는 현재 작업 범위 밖이다. 다음은 연동 구현 시 따라야 할 동작 계약이며, 변수·컴포넌트 이름이나 라이브러리를 지정하지 않는다. PRD의 모호한 경계를 아래처럼 구체화한 설계안이다.

상태 응답을 받았을 때 아래 표의 위쪽 조건부터 적용한다.

| 조건 | 화면과 버튼 | 폴링 |
| --- | --- | --- |
| 로그아웃·인증 실패·화면 이탈 | 기존 인증/이탈 처리 | 중단 |
| 응답에 boolean `analysisCompleted`가 없음 | 상태를 확인할 수 없다는 안내, 시작 비활성 | 중단 후 화면 재진입/명시적 새로고침에서 재조회 |
| `syncStatus=FAILED` | 기존 동기화 실패·재제출 안내, 시작 비활성 | 중단 |
| `submitted=false` | 기존 제출 안내, 시작 비활성 | 중단 |
| 제출 상태이며 `analysisCompleted=false` | 분석 중, 시작 비활성 | 5초 간격 유지 |
| 제출 상태이며 `analysisCompleted=true` | 분석 대기 종료, 기존 재응시 등 조건으로 시작 제어 | 중단 |

- 제출 직후와 화면 재진입 시 즉시 조회한다. 미확인 상태에서 시작을 허용하지 않는다.
- 활성 대기 화면의 5초 주기마다 요청하되 진행 중인 요청이 있으면 해당 주기는 건너뛴다.
- 네트워크·서버 조회 실패는 완료 여부 미확인으로 표시하고 시작을 비활성화한다. 인증이 유효하고 대기 화면에 머물면 같은 5초 주기로 다시 조회한다. 별도 백오프·재분석 요청은 추가하지 않는다.
- 화면 이탈·로그아웃 후 도착한 응답은 화면을 갱신하지 않는다. 같은 화면에서 요청을 중첩하지 않아 오래된 응답이 최신 상태를 덮어쓰지 않게 한다.
- `ERROR` 수신 시 요청 로딩을 해제하고 `message`를 표시한다. 분석 미완료 오류이면 준비 값을 미확인/false로 되돌리고 즉시 조회 후 대기 흐름에 진입한다. 다른 업무 오류는 분석 폴링을 무조건 재개하지 않는다.
- `analysisCompleted=true` 수신이나 WebSocket 연결/재연결만으로 면접을 자동 시작하지 않는다. 사용자 클릭과 기존 시작 조건을 확인한다.

현재 `INTERVIEW_WEBSOCKET_API.md` 12장 예제는 `onConnect`에서 면접 시작을 자동 전송한다. 구현 단계 문서 갱신 시 이를 사용자 시작 동작으로 분리해야 C4와 모순되지 않는다.

## 8. 적용·검토 기준

백엔드 계약 추가 후 프론트 연동을 적용한다. 배포된 구버전 프론트가 신규 필드·타입을 수용하는지 확인한다. 프론트에서 필드 누락을 `true`로 대체하는 호환 처리는 금지한다.

명세 검토 완료 조건은 계약 간 이름·타입 일치, 모든 FR/AC의 작업·테스트 연결, 범위 밖 정책의 미추가다. 구현 완료 조건은 [검증 명세](2026-10-01-interview-analysis-readiness-validation.md)의 두 단계 완료 기준을 따른다.

현재 `docs/`는 `.gitignore`의 로컬 공유용 제외 대상이다. 이번 문서 작업은 해당 규칙을 변경하거나 강제로 stage하지 않는다. Git으로 문서를 공유하기로 결정할 경우 구현 전에 명세·계획·검증 문서를 함께 추적하도록 별도 반영해야 한다.

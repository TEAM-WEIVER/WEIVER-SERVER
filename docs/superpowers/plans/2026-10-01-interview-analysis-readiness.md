# 면접 분석 준비 확인 및 오류 전달 구현 계획

> **구현 에이전트:** 실행 시 `superpowers:executing-plans`를 적용한다. 사용자가 서브에이전트 실행을 선택한 경우 `superpowers:subagent-driven-development`를 적용한다. 아래 체크박스는 실제 실행과 증거 확인 후에만 완료로 표시한다.

**목표:** 분석 준비 판정을 재사용해 REST 상태에 노출하고, WebSocket 업무 오류를 요청 연결에 전달한다.

**구조:** 기존 엔티티에 판단을 두고 두 서비스에서 사용한다. 기존 응답 DTO와 컨트롤러에 오류 계약을 추가하며 이벤트·DB 구조를 유지한다.

**기술:** Java 21, Spring Boot 3.5.13, JUnit 5, Mockito, AssertJ, Jackson 2, STOMP, 기존 Gradle Wrapper.

**명세:** [설계 명세](../specs/2026-10-01-interview-analysis-readiness-design.md), [PRD](../../prd/2026-10-01-interview-analysis-readiness-prd.md), [검증 명세](../specs/2026-10-01-interview-analysis-readiness-validation.md).

상태: 구현 전 계획. 실행 방식은 미선택이며 이 문서 작성만으로 구현을 시작하지 않는다.

## 공통 제약

- DB 스키마·AI 이벤트 흐름·DLQ 정책을 변경하지 않는다.
- 새 분석 상태·재분석 API·재시도 정책·캐시·외부 의존성을 추가하지 않는다.
- 기존 인증·재응시 제한·서류 작성·프로필 제출 규칙을 유지한다.
- Java 21, Spring Boot 3.5.13, Jackson 2와 기존 Gradle Wrapper를 사용한다.
- 사용자 요청 전에는 구현·커밋·푸시하지 않는다. 기존 미추적 파일과 무관한 변경을 포함하지 않는다.
- 실패를 우회하려고 테스트를 삭제하거나 `lenient`를 일괄 적용하지 않는다. mock 설정은 필요한 테스트에만 둔다.

## 리뷰 중점

| 조건 | 기대 동작 | 담당 검증 |
| --- | --- | --- |
| null·빈 문자열·탭/개행만 있는 `job/role` | 조회 false, 면접 거절 | T1·T2 / A1·A2 |
| `syncStatus=REQUESTED`인데 과거 유효 리포트가 있음 | 서버 boolean은 true; 최신 분석 완료로 설명하지 않음 | T2 / A2 |
| 기본 메시지 대신 사용자 지정 업무 메시지 | 오류 코드·메시지 유지, `error_code` 직렬화 | T3 / A4 |
| 동일 사용자의 복수 WebSocket 연결 | 요청한 연결만 업무 오류 수신 | T5 / M2 |
| 폴링 중 화면 이탈·재연결·늦은 응답 | 폴링 정리, 이탈 화면 갱신·자동 면접 시작 없음 | T4·T5 / M3 |

## 파일과 작업 순서

주요 구현 파일은 설계 명세 3절의 여섯 변경 파일이다. 신규 repository나 서비스는 없다. 테스트는 기존 서비스·컨트롤러 테스트 확장과 회귀 실행, 신규 엔티티·DTO 테스트로 구성한다.

T1 완료 후 T2, T3, T4, T5 순서로 진행한다. T2와 T3 사이 데이터 의존성은 없지만 이 계획은 순차 실행으로 작성했다. 프론트 실제 변경 경로는 프론트 저장소를 확인한 후에만 지정한다.

### T1. 공유 판단과 면접 검증

**파일**

- 변경: `src/main/java/com/weiver/analysis/domain/TechnicalSkillReport.java`
- 변경: `src/main/java/com/weiver/interview/service/InterviewFlowService.java`
- 생성: `src/test/java/com/weiver/analysis/domain/TechnicalSkillReportTest.java`
- 회귀: `src/test/java/com/weiver/interview/service/InterviewFlowServiceTest.java`

**인터페이스:** `TechnicalSkillReport.isAnalysisCompleted(): boolean`을 제공한다. 기존 `getCompletedAnalysis(Applicant)`의 반환형·예외를 유지한다. PRD FR-1, AC-8, AC-12에 대응한다.

- [ ] **1. 판단 테스트 작성:** `isAnalysisCompleted_RejectsMissingJobOrRole`에서 `{null, "", " ", "\t\n"}`의 각 값이 job 또는 role에 들어가는 8개 경우와 둘 다 null인 경우를 파라미터화한다. 반대 필드는 유효값을 넣고 반환값을 `false`로 단언한다.
- [ ] **2. 정상 경계 테스트 작성:** `isAnalysisCompleted_AcceptsTextWithoutMutatingFields`에서 `" DEVELOPER "`, `" BACKEND "`와 빈 태그 목록을 사용한다. 반환값 true, job/role 원문 보존을 단언한다. null 태그도 동일하게 확인한다.
- [ ] **3. 실패 확인:** 아래 명령을 실행해 미구현 메서드로 인한 컴파일 실패를 기록한다. JDK·네트워크 등 환경 실패는 의도한 실패로 인정하지 않는다.

```bash
./gradlew test --tests 'com.weiver.analysis.domain.TechnicalSkillReportTest'
```

- [ ] **4. 최소 구현:** C1의 메서드를 추가하고 `getCompletedAnalysis`의 필드 직접 검사를 메서드 호출로 교체한다. 서비스의 `StringUtils`가 더 이상 쓰이지 않으면 import를 제거한다.
- [ ] **5. 통과·회귀 확인:** 아래 명령에서 새 테스트와 기존 면접 서비스 테스트가 모두 통과하는지 확인한다. 기존 면접 테스트의 단언을 완화하지 않는다.

```bash
./gradlew test --tests 'com.weiver.analysis.domain.TechnicalSkillReportTest' --tests 'com.weiver.interview.service.InterviewFlowServiceTest'
```

- [ ] **6. 차이 검토:** 스키마 필드 추가가 없고 서버의 예외 코드·검증 위치·이벤트 발행 조건이 유지되는지 확인해 검증표 A1·A5에 기록한다. 커밋은 별도 요청이 있을 때만 수행한다.

### T2. 상태 응답 계약과 조회 연결

**파일**

- 변경: `src/main/java/com/weiver/applicant/dto/response/ApplicantSubmissionStatusResponseDTO.java`
- 변경: `src/main/java/com/weiver/applicant/service/ApplicantService.java`
- 테스트: `src/test/java/com/weiver/applicant/service/ApplicantServiceTest.java`
- 테스트: `src/test/java/com/weiver/applicant/controller/ApplicantControllerTest.java`

**인터페이스:** T1의 판단 메서드를 사용한다. 응답 record 마지막 인자와 접근자로 `boolean analysisCompleted`를 제공한다. PRD FR-2, AC-1~AC-5에 대응한다.

- [ ] **1. 서비스 테스트 작성:** `@Mock TechnicalSkillReportRepository`를 추가한다. 기존 `completedApplicantWithSyncStatus` fixture를 사용해 아래 표를 검증한다. C1 테스트만으로 서비스의 응답 연결 테스트를 대체하지 않는다.

| 테스트 이름 | 입력·단언 |
| --- | --- |
| `getSubmissionStatus_NoAnalysisReport_ReturnsAnalysisCompletedFalse` | `COMPLETED` + `Optional.empty()` → `analysisCompleted=false`, 기존 syncStatus 유지 |
| `getSubmissionStatus_IncompleteAnalysis_ReturnsFalse` | T1의 누락값 8개 경우를 리포트로 반환 → false |
| `getSubmissionStatus_CompletedAnalysis_ReturnsTrue` | 유효 job/role + 빈 태그 → true |
| `getSubmissionStatus_AnalysisIsIndependentOfSyncStatus` | REQUESTED + 유효 리포트 → true, REQUESTED 유지 |
| 기존 `getSubmissionStatus_ApplicantNotFound_ThrowsException` | 기존 예외 유지 + 기술 리포트 조회도 미호출 |

- [ ] **2. HTTP 테스트 작성:** 기존 `getSubmissionStatus_Success`를 false/true 파라미터 테스트로 확장한다. DTO 마지막 인자에 각 값을 넣고 기존 필드 단언에 아래 검증을 추가한다.

```java
.andExpect(jsonPath("$.data.analysisCompleted").isBoolean())
.andExpect(jsonPath("$.data.analysisCompleted").value(analysisCompleted));
```

- [ ] **3. 실패 확인:** 아래 명령에서 새 DTO 컴포넌트 부재로 인한 실패를 확인한다.

```bash
./gradlew test --tests 'com.weiver.applicant.service.ApplicantServiceTest' --tests 'com.weiver.applicant.controller.ApplicantControllerTest'
```

- [ ] **4. 최소 구현:** DTO 마지막 컴포넌트·Swagger 설명을 추가한다. 서비스에 repository를 주입하고 C2의 조회식을 응답 마지막 인자로 넣는다. 기존 `new ApplicantSubmissionStatusResponseDTO(...)` 호출부를 모두 갱신한다.
- [ ] **5. 통과 확인:** 3번과 같은 명령을 실행해 해당 테스트 전체 통과를 확인한다. 새 조회 때문에 필요한 기존 테스트의 mock은 해당 메서드에만 설정한다.
- [ ] **6. 차이 검토:** 기존 여섯 응답 필드와 지원자 부재 처리, 읽기 전용 트랜잭션이 유지되는지 확인해 A2·A3에 기록한다.

### T3. WebSocket 업무 오류 계약

**파일**

- 변경: `src/main/java/com/weiver/interview/dto/response/InterviewWebSocketMessageResponse.java`
- 변경: `src/main/java/com/weiver/interview/websocket/InterviewWebSocketController.java`
- 생성: `src/test/java/com/weiver/interview/dto/response/InterviewWebSocketMessageResponseTest.java`
- 테스트: `src/test/java/com/weiver/interview/websocket/InterviewWebSocketControllerTest.java`

**인터페이스:** `error(ErrorCode, String): InterviewWebSocketMessageResponse`, `errorCode(): String`, `handleBusinessException(BusinessException): InterviewWebSocketMessageResponse`를 제공한다. PRD FR-3, AC-6·AC-7·AC-12에 대응한다.

- [ ] **1. 변환 테스트 작성:** `handleBusinessException_ReturnsError`에서 기본 예외 메시지와 `"분석 결과를 기다려 주세요."` 사용자 지정 메시지를 각각 검증한다. 핵심 단언은 다음과 같다.

```java
assertThat(response.type()).isEqualTo("ERROR");
assertThat(response.errorCode()).isEqualTo("APPLICANT_ANALYSIS_NOT_COMPLETED");
assertThat(response.message()).isEqualTo(exception.getMessage());
assertThat(response.interviewSessionId()).isNull();
assertThat(response.status()).isNull();
```

- [ ] **2. 직렬화·정상 호환 테스트 작성:** DTO 테스트에서 Jackson 2 `ObjectMapper`로 JSON 트리를 만든다. `error_code` 값, `errorCode` 키 부재, 나머지 질문 필드의 null을 확인한다. 기존 네 팩토리 각각의 타입·세션·상태·질문·메시지와 `error_code=null`을 확인한다.
- [ ] **3. 실패 확인:** 아래 명령에서 미구현 팩토리·핸들러로 인한 실패를 확인한다.

```bash
./gradlew test --tests 'com.weiver.interview.dto.response.InterviewWebSocketMessageResponseTest' --tests 'com.weiver.interview.websocket.InterviewWebSocketControllerTest'
```

- [ ] **4. 최소 구현:** C3의 마지막 record 컴포넌트·팩토리·핸들러를 추가한다. 오류 코드는 `code.code`, 메시지는 인자를 사용한다. 기존 네 팩토리 마지막 인자는 null이다. 전역 예외 처리나 정상 메시지 방송 설정을 변경하지 않는다.
- [ ] **5. 통과 확인:** 3번과 같은 명령을 실행해 모두 통과하는지 확인한다. 직접 `startInterview()` 호출만으로 프레임 라우팅이 검증됐다고 기록하지 않는다.
- [ ] **6. 차이 검토:** `@MessageExceptionHandler(BusinessException.class)`, `@SendToUser(destinations = "/queue/interviews", broadcast = false)`와 사용자 안내 메시지를 확인해 A4에 기록한다.

### T4. API 문서와 프론트 인계

**파일:** `INTERVIEW_WEBSOCKET_API.md` 수정. 현재 미추적 문서의 기존 내용은 보존하면서 관련 절만 갱신한다. 프론트 소스 경로를 추정해 생성하지 않는다.

**인터페이스:** C2·C3를 받아 C4 동작 계약으로 전달한다. PRD FR-4·FR-5, AC-10·AC-11에 대응한다.

- [ ] **1. 8·11장 갱신:** `ERROR` 행, C3 JSON, 애플리케이션 메시지와 STOMP 오류 프레임 구분, 요청 연결 한정과 범위 밖 오류를 명시한다.
- [ ] **2. 9·12장 갱신:** 분석 준비 확인과 오류 분기를 상태 설명·예제에 추가한다. `onConnect`에서는 구독만 하고, 사용자 시작 동작에서 준비 여부·기존 제한을 확인한 뒤 publish하도록 예제를 수정한다.
- [ ] **3. 프론트 요구 전달:** C4 표를 인계하고 5초 폴링·중복 요청 방지·화면 정리·필드 누락·늦은 응답·업무 오류 메시지 표시를 검증 명세 M3의 테스트 목록으로 제공한다.
- [ ] **4. 문서 검증:** JSON 예시의 파싱과 `ERROR`, `error_code`, `/user/queue/interviews`, `analysisCompleted` 표기를 대조한다. 접속/재접속 시 자동 시작 예제가 남아 있지 않은지 확인한다.
- [ ] **5. 경계 기록:** 백엔드 문서 인계만 끝났다면 프론트 구현·검증은 미실행으로 남긴다. 실제 프론트 작업은 저장소 확인 후 M3을 구현·검증하고 파일과 증거를 기록한다.

### T5. 전체 회귀와 실제 연동 검증

**파일:** 새 제품 코드 없음. [검증 명세](../specs/2026-10-01-interview-analysis-readiness-validation.md)의 증거 표 갱신.

**인터페이스:** T1~T4 결과를 검증한다. AC-9~AC-12와 전체 회귀를 담당한다.

- [ ] **1. 전체 테스트 실행:** JDK 21·Docker 실행 조건을 확인한 후 아래 명령을 실행한다. 실제 종료 상태와 테스트 수, 실패·건너뜀을 기록한다.

```bash
./gradlew test
```

- [ ] **2. 실제 폴링 검증:** 검증 명세 M1을 실행한다. 이벤트 수신 로그만으로 완료를 판정하지 않는다.
- [ ] **3. 실제 STOMP 검증:** M2에서 같은 사용자 연결 A·B를 구독하고 A의 미완료 시작 오류가 A에만 전달되는지 확인한다. 답변 업무 오류와 분석 완료 후 정상 흐름도 확인한다.
- [ ] **4. 프론트 검증:** 프론트 변경이 준비되면 M3을 실행한다. 준비되지 않았다면 미실행으로 남기고 사용자 흐름 완료로 표시하지 않는다.
- [ ] **5. 범위 검토:** `git diff --check`, `git diff --stat`, `git status --short`로 변경을 확인한다. 미추적 API 문서와 Git 제외 대상 SDD 문서는 별도로 확인한다. DB·이벤트·DLQ 관련 변경이 없어야 한다.
- [ ] **6. 완료 판정:** 모든 기준을 만족한 단계만 검증 명세에 완료로 기록한다. 커밋·푸시·배포는 별도 사용자 요청이 있을 때만 한다.

## 실행 인계

이 계획은 구현 준비 문서이며 현재 체크박스는 모두 미실행이다. 구현 요청을 받으면 PRD·설계·검증 명세를 함께 읽고 실행 방식을 확정한 뒤 T1부터 진행한다. 직접 순차 실행을 기본 제안으로 둔다. 변경 범위가 작고 DTO 계약을 공유하므로 작업별 환경을 나누는 이점이 크지 않다.

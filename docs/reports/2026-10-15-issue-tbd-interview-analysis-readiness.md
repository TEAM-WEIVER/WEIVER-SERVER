# 면접 분석 준비 확인 및 오류 전달 구현 보고서

- Issue: 아직 없음(기능 Issue 생성 후 파일 이름과 이 줄을 갱신). 보고 절차 시범 적용은 #163
- 브랜치 / 기준 커밋: 구현 브랜치 없음 / `d707797` (명세 작성 기준 `5e5dd5a`에서 `d707797`까지 아래 근거 코드 줄 번호를 다시 확인함)
- 관련 명세·계획: [설계 명세](../superpowers/specs/2026-10-01-interview-analysis-readiness-design.md) · [검증 명세](../superpowers/specs/2026-10-01-interview-analysis-readiness-validation.md) · [구현 계획](../superpowers/plans/2026-10-01-interview-analysis-readiness.md) · PRD(로컬 전용, Git 미추적)
- 보고 수준: 전체 보고
- 상태: 구현 전 (#163에서 보고 형식 점검용으로 1절만 작성. 2~5절은 기능 구현 PR에서 작성)

## 1. 구현 전 — 문제·대안·선택 근거

### 문제

1. **분석 완료 판단이 면접 서비스 안에만 있다.** `job`·`role`이 둘 다 텍스트가 있어야 분석 완료로 보는 규칙이
   private 메서드 `getCompletedAnalysis`(`src/main/java/com/weiver/interview/service/InterviewFlowService.java:408`)의 조건문(`:412`)에만 있다.
   지원자 제출 상태 조회(`src/main/java/com/weiver/applicant/service/ApplicantService.java:109`)는 이 판단을 쓰지 않고,
   응답 `ApplicantSubmissionStatusResponseDTO`(`src/main/java/com/weiver/applicant/dto/response/ApplicantSubmissionStatusResponseDTO.java:7`)에도
   분석 완료 필드가 없다. 그래서 프론트는 면접 시작 전에 분석 완료 여부를 알 수 없다.
2. **면접 시작 거절이 클라이언트에 전달되지 않는다.** 아래 호출 흐름에서 분석이 끝나지 않았으면
   `BusinessException(APPLICANT_ANALYSIS_NOT_COMPLETED)`가 던져진다. 그런데 `src/main/java/` 전체에 `@MessageExceptionHandler`가 없다
   (`grep -rn MessageExceptionHandler src/main/java` 결과 0건). 그래서 이 업무 오류가 `/user/queue/interviews` 구독 채널로 돌아가지 않는다.

현재 호출 흐름(`d707797` 기준):

```text
STOMP SEND /app/interviews/start
  → InterviewWebSocketController.startInterview (src/main/java/com/weiver/interview/websocket/InterviewWebSocketController.java:32)
    → InterviewFlowService.startInterview (src/main/java/com/weiver/interview/service/InterviewFlowService.java:74)
      → publishQuestionRequested(session, null, 1) (InterviewFlowService.java:91 → :383)
        → getCompletedAnalysis(applicant) (InterviewFlowService.java:385 → :408)
          → findByApplicant 없음 또는 job/role 공백 → BusinessException(APPLICANT_ANALYSIS_NOT_COMPLETED) (InterviewFlowService.java:410, :413)
```

### 대안

| 대안 | 장점 | 단점·위험 |
| --- | --- | --- |
| A. 판단을 엔티티 `TechnicalSkillReport.isAnalysisCompleted()`로 옮기고 두 서비스가 같이 사용 | 판단이 한 곳에 있다. 기존 "상태 판단은 엔티티 편의 메서드" 규칙(backend-conventions)과 맞다 | 엔티티 메서드 추가. 단, DB 스키마는 바뀌지 않는다 |
| B. `ApplicantService`에 같은 조건을 한 번 더 작성 | 변경 파일이 적다 | 규칙이 두 곳으로 갈라져 나중에 어긋날 수 있다 |
| C. 분석 상태 enum이나 새 컬럼 추가 | 상태를 명시적으로 표현 | DB 스키마 변경(ddl-auto)이고 명세의 불변 조건("새 분석 상태 추가 안 함")과 충돌 |

오류 전달은 컨트롤러 단위 `@MessageExceptionHandler` + `@SendToUser`(요청 연결로만 전달)와, 전역 처리기나 사용자 전체 브로드캐스트 중에서 고른다.
명세는 동일 사용자의 다른 연결에 오류가 가지 않아야 한다고 정했다(검증 명세 M2).

### 선택과 근거

| 결정 | 선택 | 근거 | 출처 |
| --- | --- | --- | --- |
| 판단 위치 | A | 판단 일원화, 엔티티 위임 규칙, 스키마 불변 | 명세 기재(설계 명세 3·4절) |
| REST 노출 | `ApplicantSubmissionStatusResponseDTO` 마지막 컴포넌트로 `boolean analysisCompleted` 추가 | 기존 필드 순서를 유지해 응답 호환 | 명세 기재(설계 명세 5절). **공개 API 변경이라 구현 전 사용자 결정으로 받아야 함** |
| 오류 전달 | `InterviewWebSocketController`에 `@MessageExceptionHandler(BusinessException.class)` + `@SendToUser(destinations = "/queue/interviews", broadcast = false)`, 응답에 `error_code`(`errorCode`) 추가 | 요청한 연결만 오류 수신(M2), 기존 `ErrorCode` 재사용 | 명세 기재(설계 명세 6절). **공개 API 변경이라 구현 전 사용자 결정으로 받아야 함** |

### 영향 범위와 검증 계획

- 수정 대상과 호출부: `TechnicalSkillReport`, `InterviewFlowService.getCompletedAnalysis`(호출부 `:385` 1곳. 이 호출을 감싼 `publishQuestionRequested`는 면접 시작 `:91`과 답변 제출 `:127` 두 곳에서 불리므로 답변 제출 경로에도 영향), `ApplicantService.getSubmissionStatus`, `ApplicantSubmissionStatusResponseDTO`, `InterviewWebSocketMessageResponse`, `InterviewWebSocketController` — 설계 명세 3절의 여섯 파일과 같다.
- 기존 테스트: `InterviewFlowServiceTest`, 지원자 서비스·컨트롤러 테스트, WebSocket 컨트롤러 테스트. 단언을 완화하지 않는다.
- 실행할 검증: 구현 계획 T1~T5의 단계별 명령, 마지막에 `./gradlew test`와 `bash .github/scripts/check-docs.sh`.
- 승인이 필요한 변경: REST 응답 필드 추가, WebSocket 응답 필드(`errorCode`) 추가 — 공개 API 계약 변경. DB 스키마 변경 없음.

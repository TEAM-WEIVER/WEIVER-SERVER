# AI 개발 하네스 구현 판단·코드 이해 보고 절차 구현 보고서

- Issue: #163
- 브랜치 / 기준 커밋: `chore/#163-ai-development-harness` / `d707797` (작업 시작 시 `origin/main`)
- 관련 명세·계획: 없음(Issue 본문이 요구사항의 정본)
- 보고 수준: 전체 보고
- 상태: 구현 후

## 1. 구현 전 — 문제·대안·선택 근거

### 문제

- AI 구현자의 인계 형식이 다섯 줄(변경 파일·컨텍스트·핵심 결정·실행한 검증·남은 위험)뿐이었다.
  대안과 근거, 판단 변경, 호출 흐름, 검증 상태를 남길 자리가 없었다(`d707797:.claude/agents/backend-developer.md`).
- 리뷰어는 미커밋 변경만 봤다(`git status`/`git diff`). 브랜치·PR 단위 기준이 없었고, 구현자의 설명을 코드와 대조하는 절차도 없었다(`d707797:.claude/agents/code-reviewer.md`).
- 공통 규칙이 `CLAUDE.md`에만 있어 다른 AI 도구와 공유할 파일이 없었다. `AGENTS.md`도 없었다.
- `.gitignore`가 `docs/`를 통째로 무시해서, 명세와 보고서를 팀과 공유할 수 없었다.
- CI는 `main` push에서 테스트를 건너뛰고(`-x test`) 배포만 했다. PR 단계의 검증이 없었다.
- 기준선(`d707797`, `./gradlew clean test --rerun-tasks --no-build-cache`, contextLoads 제외): 63클래스 411테스트 모두 통과, 실패 0, 건너뜀 0.

### 대안

| 주제 | 대안 | 장점 | 단점·위험 |
| --- | --- | --- | --- |
| 공통 규칙 위치 | A. `AGENTS.md`로 옮기고 `CLAUDE.md`는 `@AGENTS.md`로 가져오기 | 한 곳만 고치면 된다 | 팀이 쓰는 `CLAUDE.md`를 크게 바꾼다 |
| | B. `AGENTS.md`만 새로 만들고 `CLAUDE.md` 유지 | 기존 파일을 건드리지 않는다 | 같은 규칙이 두 파일에 생기고 어긋날 수 있다 |
| docs 추적 | A. 새 템플릿·보고서 + 면접 분석 준비 확인 명세 3개 | 9번 시범에 필요한 것만 공개 | 명세가 로컬 전용 PRD를 링크한다 |
| | B. `docs/prd`까지 포함 | 링크가 모두 열린다 | 공개 저장소에 PRD 2개 노출 |
| PR CI | A. 문서 검사 + `./gradlew test`(contextLoads 제외) | 테스트 회귀를 PR에서 잡는다 | 실제 DB가 필요한 테스트 1개는 미실행 |
| | B. 문서 검사만 | 빠르다 | 코드 회귀를 못 잡는다 |
| 오류 감지 확인 | A. 검사기 안에 자체 검사(깨진 링크 예시가 실패하는지) | 매 실행마다 증명되고, 일부러 빨간 커밋을 남기지 않는다 | 검사기 코드가 길어진다 |
| | B. 일부러 깨진 커밋을 push했다가 되돌리기 | 실제 CI 실패를 한 번 본다 | 팀 브랜치에 실패 기록이 남는다 |

### 선택과 근거

| 결정 | 선택 | 근거 | 출처 |
| --- | --- | --- | --- |
| 공통 규칙 위치 | A | 중복 제거. Claude Code는 `CLAUDE.md`가 있으면 `AGENTS.md`를 직접 읽지 않으므로 가져오기가 필요 | 사용자 결정(diff 확인 후 승인) |
| 커밋·push 규칙 문구 | "명시적 요청 또는 작업 시작 시 허용한 범위" | 이번처럼 Issue 브랜치 단위로 미리 허용하는 방식을 규칙에 반영 | 사용자 결정("변경안으로 가자") |
| 승인 대상 확장, contextLoads 경고, 비밀값 규칙 | 추가 | 기존 규칙에서 빠져 있던 위험 | 사용자 결정 |
| docs 추적 범위 | A, PRD는 이번에 올리지 않음 | 공개 범위 최소화 | 사용자 결정("prd는 이번에 올리지마") |
| PRD 링크 처리 | 명세 원문은 고치지 않고, 링크 검사에서 `docs/prd/`를 로컬 전용으로 건너뜀 집계(통과로 세지 않음) | 사용자가 고치라고 하지 않은 명세를 수정하지 않음 | 승인된 범위 안의 구현 판단 |
| PR CI | A | | 사용자 결정 |
| 오류 감지 확인 | A(자체 검사) | 위 표 | 승인된 범위 안의 구현 판단(advisor 제안 반영) |
| 링크 검사 기준 | 링크 대상이 Git 추적 파일이어야 통과 | 로컬에만 있는 파일이 로컬 검사는 통과하고 CI에서만 깨지는 차이를 없앤다 | 승인된 범위 안의 구현 판단 |
| 검사기 구현 | bash 진입점 + python3 표준 라이브러리 | 새 의존성·외부 Action 없음 | 승인된 범위 안의 구현 판단 |
| contextLoads 제외 방법 | `.github/ci/` Gradle init 스크립트 | `build.gradle`과 테스트 코드를 고치지 않는다 | 승인된 범위 안의 구현 판단 |
| 보고서 파일 이름 | `YYYY-MM-DD-issue-<번호>-<설명>.md`(`#` 없음) | `#`은 링크에서 URL 조각으로 해석된다 | 승인된 범위 안의 구현 판단 |

### 영향 범위와 검증 계획

- 수정 대상: 하네스 문서(`AGENTS.md`, `CLAUDE.md`, `.claude/agents/*`, `.claude/commands/codereview.md`), `.github/` 템플릿, 새 워크플로·스크립트, `.gitignore`, `docs/`.
- Java 코드, `build.gradle`, 기존 `deploy.yml`, 저장소 설정은 바꾸지 않는다.
- 검증: 문서 검사 로컬 실행, 검사기 변형(고의 고장) 시 자체 검사 실패 확인, PR CI 두 job 결과 확인.
- 승인이 필요한 변경: 팀 공유 `CLAUDE.md` 변경(승인 받음), 새 PR 워크플로(승인 받음). 새 권한 부여는 없음(아래 2절 참고).

## 2. 구현 중 — 판단 변경

| 원래 판단 | 바뀐 판단 | 바꾼 이유(발견한 사실) | 출처 |
| --- | --- | --- | --- |
| `codereview.md`를 감싼 코드 펜스(```)를 지우고 frontmatter를 살린다 | 펜스를 그대로 두고 문구만 고친다 | 펜스 때문에 지금은 frontmatter가 해석되지 않는다. 지우면 `allowed-tools: ... Bash`가 새로 적용돼 권한이 바뀐다 | 승인된 범위 안의 구현 판단(권한 변경은 승인 대상이라 하지 않음) |
| 명세 3개를 그대로 올리면 링크가 모두 열린다고 봤다 | PRD 링크 3곳(처음에 4곳으로 잘못 셈)을 로컬 전용 건너뜀으로 처리 | 명세 3개가 모두 Git에 올리지 않는 PRD를 링크한다 | 사용자 결정(PRD 미공개) + 구현 판단(처리 방식) |
| Issue 템플릿 끝에 새 절을 덧붙인다 | `### 유의사항` 앞에 넣는다 | 원래 파일 끝에 줄바꿈이 없어 새 절이 제목에 붙었다 | 승인된 범위 안의 구현 판단 |
| 결정 출처를 셋(AI 제안·사용자 결정·구현 판단)으로 나눈다 | "명세 기재"를 넷째 출처로 추가하고, Issue가 없을 때 파일 이름에 `tbd`를 쓰는 규칙을 추가한다 | 9번 시범에서 명세에만 적히고 승인 기록이 없는 결정을 분류할 수 없었다. 기능 Issue도 아직 없었다 | 승인된 범위 안의 구현 판단(9번 점검 결과) |

## 3. 구현 후 — 호출 흐름·핵심 코드·검증 결과

### 변경 파일

| 파일 | 변경 요약 |
| --- | --- |
| `AGENTS.md`(새 파일) | 프로젝트 개요, 매뉴얼 지도(링크), 공통 작업 흐름 7단계, 승인·중단·완료 기준, 검증(문서 검사·PR CI 포함) |
| `CLAUDE.md` | `@AGENTS.md` + 에이전트 라우팅만 남김 |
| `docs/templates/implementation-report.md`(새 파일) | 구현 전·중·후, 완료 상태, 리뷰 대조, 짧은 보고 양식 |
| `.claude/agents/backend-developer.md` | 필수 문서, 구현 전·중·후 보고 단계, 인계 형식 확장 |
| `.claude/agents/code-reviewer.md` | 필수 문서, 리뷰 대상 구분(미커밋/브랜치/PR), 보고서 대조, 출력에 대조 표 |
| `.claude/commands/codereview.md` | 리뷰 모드 인자, 프로젝트 이름 수정 |
| `.github/PULL_REQUEST_TEMPLATE.md`, `.github/ISSUE_TEMPLATE/*` | 관련 명세·보고서, 검증 결과 항목 |
| `.gitignore` | `docs/*` + 공유 경로 예외 |
| `docs/superpowers/{specs,plans}/…readiness*`(3개) | 기존 로컬 명세를 Git에 추가(내용 변경 없음) |
| `.github/scripts/check-docs.sh`(새 파일) | 필수 문서·로컬 링크 검사 + 자체 검사 |
| `.github/workflows/pr-check.yml`, `.github/ci/exclude-context-loads.gradle`(새 파일) | PR CI |
| `docs/reports/2026-10-15-issue-tbd-interview-analysis-readiness.md`(새 파일) | 9번 시범 보고서(구현 전) |

### 호출 흐름

```text
pull_request(main 대상) → .github/workflows/pr-check.yml
  ├ job docs: bash .github/scripts/check-docs.sh
  │   → self_test(): 임시 디렉터리 예시 8개로 check() 판정 확인 — 기대와 다르면 종료 코드 1
  │   → check(저장소): REQUIRED_DOCS 존재·내용 확인 → git ls-files의 *.md 링크를 추적 파일 목록과 대조
  │   → 오류 1개 이상이면 종료 코드 1
  └ job test: ./gradlew test --no-daemon -I .github/ci/exclude-context-loads.gradle
      → init 스크립트가 모든 Test 태스크에서 WeiverBackendApplicationTests 제외
```

### 핵심 코드

| 위치 | 하는 일 | 왜 이렇게 했는가 |
| --- | --- | --- |
| `.github/scripts/check-docs.sh`의 `check()` | 링크 대상을 `git ls-files` 목록과 대조. `docs/prd/`는 건너뜀, 로컬에만 있는 파일은 "Git이 추적하지 않는 파일" 오류 | 로컬과 CI의 판정을 같게 만든다 |
| `.github/scripts/check-docs.sh`의 `self_test()` | 정상 링크 통과, 깨진 링크·앵커 붙은 깨진 링크·필수 문서 누락·필수 내용 누락·저장소 밖 링크 실패, 코드 블록 무시, 로컬 전용 건너뜀 — 8개 경우를 판정 | 검사기가 망가져 늘 통과하는 상황을 매 실행마다 잡는다 |
| `.github/workflows/pr-check.yml` | `pull_request` 트리거, `permissions: contents: read` | `pull_request_target`을 쓰지 않아 포크 PR에 비밀값·쓰기 권한이 넘어가지 않는다 |

### 검증 결과

| 검증 | 명령 | 상태 | 증거 |
| --- | --- | --- | --- |
| 문서 검사(로컬) | `bash .github/scripts/check-docs.sh` | 통과 | 자체 검사 통과, 통과 링크 18개·건너뜀(로컬 전용) 3개·오류 0개(보고서 추가 후 기준, PR 본문에 최종값) |
| 오류 감지 확인(로컬) | 검사기 복사본 2개를 고의로 고장(링크 정규식 무력화 / 깨진 링크를 통과로 셈) | 통과 | 두 복사본 모두 `[자체 검사 실패]`, 종료 코드 1 |
| 기준선 테스트(로컬) | `./gradlew clean test --rerun-tasks --no-build-cache -I <제외 init 스크립트>` | 통과 | `d707797`에서 63클래스 411테스트 통과. 이 PR은 Java·Gradle 파일을 바꾸지 않아 변경 뒤 로컬 재실행은 하지 않았다(미실행) |
| PR CI docs job | `pr-check / 문서·링크 검사` | 통과 | [실행 38718945331](https://github.com/TEAM-WEIVER/WEIVER-SERVER/actions/runs/38718945331) — 자체 검사 통과, 통과 링크 18 · 건너뜀 3 · 오류 0 (커밋 `2288ac6`) |
| PR CI test job | `pr-check / 테스트 (contextLoads 제외)` | 통과 | [실행 38718945331](https://github.com/TEAM-WEIVER/WEIVER-SERVER/actions/runs/38718945331) — 로그에서 PASSED 411 · FAILED 0 · SKIPPED 0, Testcontainers 리포지토리 테스트 4클래스 실행 확인, `WeiverBackendApplicationTests` 0건(제외 확인) |
| contextLoads | — | 미실행 | 로컬 DB(create-drop) 필요. 로컬·CI 모두 제외 |
| 9번: 실제 기능 구현에 적용 | — | 미실행 | 기능 구현은 이 Issue 범위 밖. 구현 전 보고서만 시범 작성 |

### 남은 위험·후속 작업

- `pr-check`를 필수 검사로 지정하는 것은 저장소 보호 규칙 변경이라 이 PR에서 하지 않았다.
- `codereview.md`는 코드 펜스 때문에 frontmatter(`description`, `allowed-tools`)가 해석되지 않는다. 펜스를 지울지는 권한 변경이라 별도 결정이 필요하다.
- 링크 검사는 파일 존재만 보고 앵커(`#절`)가 실제로 있는지는 확인하지 않는다.
- 면접 분석 준비 확인 기능 Issue를 만들면 시범 보고서의 파일 이름(`issue-tbd`)과 Issue 줄을 갱신해야 한다.

## 4. 완료 상태

- [x] 구현 완료 — 체크리스트 1~8번 변경을 커밋했다.
- [ ] 검증 완료 — PR CI 결과와 리뷰 대조를 확인한 뒤 체크한다. contextLoads와 9번 실제 기능 적용은 미실행이다.
- [ ] 설명 전달 완료 — PR 본문에 이 보고서 링크와 검증 결과를 적은 뒤 체크한다.

## 5. 리뷰 대조 (리뷰어 작성)

| 보고서의 주장 | 확인한 코드·증거 | 결과 |
| --- | --- | --- |
| | | |

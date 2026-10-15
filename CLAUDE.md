# CLAUDE.md

@AGENTS.md

위 `AGENTS.md`가 이 저장소의 공통 규칙(프로젝트 개요, 매뉴얼 지도, 작업 흐름, 승인·중단·완료 기준,
검증)이다. 이 파일에는 Claude Code에만 해당하는 내용만 둔다.

## 에이전트 라우팅

| 역할 | 파일 | 권한 |
| --- | --- | --- |
| backend-developer | `.claude/agents/backend-developer.md` | 배정 범위의 유일한 작성자. 계층형 구조에 맞춰 기능 구현 |
| code-reviewer | `.claude/agents/code-reviewer.md` | 읽기 전용. 변경 파일을 아키텍처·프로젝트 규칙과 대조해 리뷰. 코드 수정 안 함 |

- 매뉴얼 지도의 규약 문서는 `.claude/skills/`의 스킬로 등록돼 있다. 범위에 맞는 스킬만 불러온다.
- 서브에이전트는 이 파일과 `AGENTS.md`를 보지 못할 수 있다. 각 에이전트 정의 파일에 꼭 읽을 문서 경로를 직접 적는다.

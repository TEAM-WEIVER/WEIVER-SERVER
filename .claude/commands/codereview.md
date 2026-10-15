```
---
description: 변경된 파일을 WEIVER 서버 계층형 아키텍처·컨벤션 기준으로 코드리뷰합니다 (인자 없음=미커밋 변경, branch=브랜치 전체, PR 번호=PR)
allowed-tools: Read, Grep, Glob, Bash
---

## Task

code-reviewer agent 를 실행하여 코드리뷰해

리뷰 모드: $ARGUMENTS
- 비어 있으면 미커밋 변경 모드
- `branch`이면 브랜치 모드(`origin/main...HEAD`)
- 숫자이면 그 번호의 PR 모드

@.claude/agents/code-reviewer.md
```


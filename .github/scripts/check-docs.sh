#!/usr/bin/env bash
# 하네스 필수 문서와 마크다운 로컬 링크를 검사한다.
#
#   사용: bash .github/scripts/check-docs.sh [저장소 루트]
#
# - 매 실행마다 먼저 자체 검사(깨진 링크·누락 문서 예시가 실제로 실패로 잡히는지)를 돌린다.
#   자체 검사가 기대와 다르면 검사기 자체가 고장 난 것으로 보고 실패한다.
# - 링크 대상은 "Git이 추적하는 파일"이어야 통과한다. 로컬에만 있는 파일은 CI에서 없으므로 오류다.
# - 외부 URL, 앵커(#...)만 있는 링크, 펜스 코드 블록(```, ~~~)·인라인 코드 안의 링크는 검사하지 않는다.
#   앵커가 붙은 링크는 파일 존재만 확인하고 앵커가 실제로 있는지는 보지 않는다.
# - `/`로 시작하는 링크는 저장소 루트 기준으로, 퍼센트 인코딩(%20 등)은 풀어서 해석한다.
# - 지원하지 않는 형식(검사하지 않고 지나감): 참조형 링크([r]: x.md), HTML <a href>/<img src>,
#   공백이 든 꺾쇠 링크(<a b.md>). 4칸 들여쓴 코드 블록 안의 링크는 검사 대상이 된다.
# 의존성: bash, git, python3(표준 라이브러리만).
set -euo pipefail

ROOT="${1:-$(git rev-parse --show-toplevel)}"

exec python3 -I - "$ROOT" <<'PY'
import os
import re
import subprocess
import sys
import tempfile
import urllib.parse

# (경로, 반드시 포함해야 하는 문자열 목록)
REQUIRED_DOCS = [
    ("AGENTS.md", ["## 매뉴얼 지도", "## 승인 기준", "## 중단 기준", "## 완료 기준"]),
    ("CLAUDE.md", ["@AGENTS.md"]),
    (".claude/templates/implementation-report.md", ["## 1. 구현 전", "## 2. 구현 중", "## 3. 구현 후", "## 5. 리뷰 대조"]),
    (".claude/agents/backend-developer.md", ["AGENTS.md", ".claude/templates/implementation-report.md"]),
    (".claude/agents/code-reviewer.md", ["AGENTS.md", ".claude/templates/implementation-report.md", "## 보고서 대조"]),
    (".github/PULL_REQUEST_TEMPLATE.md", ["## 📚 구현 보고", "## ✅ 검증 결과"]),
]

# git hook 등에서 상속된 GIT_DIR·GIT_WORK_TREE가 있으면 -C <root>가 무시되므로 지운다.
GIT_ENV = {k: v for k, v in os.environ.items() if k not in ("GIT_DIR", "GIT_WORK_TREE", "GIT_INDEX_FILE")}

LINK_RE = re.compile(r"!?\[[^\]]*\]\(\s*<?([^)\s>]+)>?(?:\s+\"[^\"]*\")?\s*\)")
INLINE_CODE_RE = re.compile(r"`+[^`]*`+")
FENCE_RE = re.compile(r"^\s*(```|~~~)")
EXTERNAL_RE = re.compile(r"^[a-zA-Z][a-zA-Z0-9+.-]*:")  # http:, https:, mailto: 등


def list_files(root):
    """Git 저장소면 추적 파일, 아니면 디렉터리의 모든 파일(자체 검사용)."""
    try:
        out = subprocess.run(
            ["git", "-C", root, "ls-files", "-z"],
            check=True, capture_output=True, env=GIT_ENV,
        ).stdout.decode("utf-8")
        return {p for p in out.split("\0") if p}
    except (subprocess.CalledProcessError, FileNotFoundError):
        files = set()
        for d, _, names in os.walk(root):
            for n in names:
                files.add(os.path.relpath(os.path.join(d, n), root).replace(os.sep, "/"))
        return files


def iter_links(text):
    in_fence = False
    for lineno, line in enumerate(text.splitlines(), 1):
        if FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if in_fence:
            continue
        for m in LINK_RE.finditer(INLINE_CODE_RE.sub("", line)):
            yield lineno, m.group(1)


def check(root):
    """(오류 목록, 통과 링크 수)를 돌려준다."""
    files = list_files(root)
    dirs = {os.path.dirname(f) for f in files}
    for f in list(dirs):
        while f:
            f = os.path.dirname(f)
            dirs.add(f)

    errors, ok = [], 0

    for path, needles in REQUIRED_DOCS:
        if path not in files:
            errors.append(f"필수 문서 없음(또는 Git 미추적): {path}")
            continue
        with open(os.path.join(root, path), encoding="utf-8") as fh:
            text = fh.read()
        for n in needles:
            if n not in text:
                errors.append(f"필수 내용 없음: {path} 에 '{n}'")

    for md in sorted(f for f in files if f.endswith(".md")):
        with open(os.path.join(root, md), encoding="utf-8") as fh:
            text = fh.read()
        base = os.path.dirname(md)
        for lineno, target in iter_links(text):
            if EXTERNAL_RE.match(target) or target.startswith("#"):
                continue
            target_path = urllib.parse.unquote(target.split("#", 1)[0].split("?", 1)[0])
            if target_path.startswith("/"):
                resolved = os.path.normpath(target_path.lstrip("/")).replace(os.sep, "/")
            else:
                resolved = os.path.normpath(os.path.join(base, target_path)).replace(os.sep, "/")
            where = f"{md}:{lineno} -> {target}"
            if resolved.startswith("../") or resolved == "..":
                errors.append(f"저장소 밖을 가리키는 링크: {where}")
            elif resolved in files or resolved in dirs:
                ok += 1
            elif os.path.exists(os.path.join(root, resolved)):
                errors.append(f"Git이 추적하지 않는 파일을 가리키는 링크: {where}")
            else:
                errors.append(f"깨진 링크: {where}")
    return errors, ok


def write(root, rel, text):
    p = os.path.join(root, rel)
    os.makedirs(os.path.dirname(p), exist_ok=True)
    with open(p, "w", encoding="utf-8") as fh:
        fh.write(text)


def make_valid_fixture(root):
    for path, needles in REQUIRED_DOCS:
        write(root, path, "\n".join(needles) + "\n")


def self_test():
    """검사기가 오류 사례를 실제로 실패로 잡는지 확인한다. 실패한 기대 목록을 돌려준다."""
    cases = []

    def case(name, extra_files, *, remove=None, expect_errors, git=False, untracked=()):
        with tempfile.TemporaryDirectory() as root:
            make_valid_fixture(root)
            for rel, text in extra_files.items():
                write(root, rel, text)
            if remove:
                os.remove(os.path.join(root, remove))
            if git:
                # 실제 저장소처럼 git ls-files 경로를 타게 한다. untracked 파일은 디스크에만 둔다.
                subprocess.run(["git", "init", "-q", root], check=True, env=GIT_ENV)
                subprocess.run(["git", "-C", root, "add", "-A"], check=True, env=GIT_ENV)
                for rel in untracked:
                    subprocess.run(["git", "-C", root, "rm", "-q", "--cached", rel], check=True, env=GIT_ENV)
            errors, _ = check(root)
            if (len(errors) > 0) != expect_errors:
                cases.append(f"{name}: 기대 오류 있음={expect_errors}, 실제 오류 있음={len(errors) > 0} {errors}")

    case("정상 링크는 통과", {"notes/a.md": "[b](b.md)\n", "notes/b.md": "x\n"}, expect_errors=False)
    case("깨진 링크는 실패", {"notes/a.md": "[없음](missing.md)\n"}, expect_errors=True)
    case("앵커가 붙은 깨진 링크도 실패", {"notes/a.md": "[없음](missing.md#sec)\n"}, expect_errors=True)
    case("필수 문서 누락은 실패", {}, remove="AGENTS.md", expect_errors=True)
    case("필수 내용 누락은 실패", {"CLAUDE.md": "가져오기 없음\n"}, expect_errors=True)
    case("코드 블록 안 링크는 무시", {"notes/a.md": "```\n[없음](missing.md)\n```\n`[없음](missing.md)`\n"}, expect_errors=False)
    case("저장소 밖 링크는 실패", {"a.md": "[밖](../x.md)\n"}, expect_errors=True)
    case("퍼센트 인코딩 링크는 통과", {"notes/a.md": "[b](my%20file.md)\n", "notes/my file.md": "x\n"}, expect_errors=False)
    case("루트 기준 링크는 통과", {"notes/a.md": "[b](/notes/b.md)\n", "notes/b.md": "x\n"}, expect_errors=False)
    case("Git 추적 파일 링크는 통과", {"notes/a.md": "[b](b.md)\n", "notes/b.md": "x\n"}, expect_errors=False, git=True)
    case("Git 미추적 파일 링크는 실패", {"notes/a.md": "[b](b.md)\n", "notes/b.md": "x\n"}, expect_errors=True, git=True, untracked=("notes/b.md",))
    return cases


def main():
    root = sys.argv[1]

    failed = self_test()
    if failed:
        print("[자체 검사 실패] 검사기가 오류 사례를 기대대로 판정하지 못했다:")
        for f in failed:
            print(f"  - {f}")
        return 1
    print("[자체 검사 통과] 깨진 링크·누락 문서 예시를 실패로 잡는 것을 확인했다.")

    errors, ok = check(root)
    for e in errors:
        print(f"[오류] {e}")
    print(f"결과: 통과 링크 {ok}개, 오류 {len(errors)}개")
    return 1 if errors else 0


sys.exit(main())
PY

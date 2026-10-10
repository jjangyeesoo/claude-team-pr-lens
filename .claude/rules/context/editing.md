---
paths:
  - ".claude/**"
  - "**/CLAUDE.md"
---
# 컨텍스트 파일 수정 규칙
팀이 공유하는(git에 커밋되는) 파일에 적용한다. gitignore된 개인 파일(`.claude/settings.local.json`, `CLAUDE.local.md` 등)은 해당 없다.
- 브랜치: 기능 브랜치(`feat/`, `fix/`)에서 고치지 않는다. `origin/main`에서 `chore/<slug>`를 만들어 작업한다(`tasks.md`에 번호가 있는 컨텍스트 작업은 `chore/<단계>-<번호>-<slug>`. `/task`가 이 이름으로 진행 중인 작업을 찾는다). PR에는 `context` 라벨을 붙인다. 승인자는 `docs/product/PLAYBOOK.md` "승인 규칙 요약" 표를 따른다
- 고치기 전에 같은 내용을 적은 곳을 찾는다. 바꾸는 skill·agent 이름, 단계 번호, 용어로 `.claude/`, 모든 `CLAUDE.md`, 루트 `README.md`, `docs/guide/`, `docs/product/PLAYBOOK.md`, `.github/pull_request_template.md`를 Grep한다
- 서로 맞물린 파일은 같은 PR에서 함께 고친다
  - `/task` ↔ `/pr-ready` ↔ `reviewer`: 용어, 단계 번호, 보고 그룹("반드시 수정", "선택", "뒤 작업에 넘길 것")
  - `/pr-ready` 3·4번 ↔ `context-reviewer`(보고 그룹 두 개), 이 파일의 Grep 대상 목록 ↔ `context-reviewer` 2번
  - 브랜치 이름 형식 ↔ 루트 `CLAUDE.md` Conventions, `/task`의 진행 중 판정과 브랜치 맞추기, `/pr-ready` 5번의 진행 중 판정
  - `/pr-ready`가 만드는 PR 설명 ↔ `.github/pull_request_template.md`
  - skill과 agent의 절차 ↔ `docs/guide/task-workflow.md` (사람이 읽는 안내서. 원본은 skill이다)
  - `.claude/settings.json`, `.claude/hooks/` ↔ `docs/guide/claude-code-setup.md`
  - `.claude/`의 파일 목록(agents, rules, skills, hooks) ↔ 루트 `README.md`의 구조 트리
  - 브랜치·PR 단위·승인 규칙 ↔ `docs/product/PLAYBOOK.md`
- 규칙은 한 곳에만 적고 다른 곳에서는 그곳을 가리킨다. 루트 `CLAUDE.md`는 200줄 미만으로 유지하고, 특정 경로에서만 필요한 내용은 `.claude/rules/`(`paths`)나 그 폴더의 `CLAUDE.md`에 둔다
- 권한을 넓히는 변경(`settings.json`의 deny 삭제, allow 추가, hook 완화)은 이유를 먼저 설명하고 묻는다
- 리뷰는 `context-reviewer` 에이전트로 한다 (`reviewer`는 코드용이다). 커밋하지 않은 상태로 맡길 때는 에이전트에게 넘기는 요청에 그 사실과 새 파일 경로를 적는다
- 바꾼 skill이나 agent는 실제 브랜치에서 한 번 실행해 본다. 실행 결과는 리뷰를 맡길 때 함께 넘긴다. 실행해 보지 못했으면 PR 설명의 "검증 증거"에 그렇게 적는다

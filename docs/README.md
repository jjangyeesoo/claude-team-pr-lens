# 문서 지도

각 주제의 원본이 어느 문서인지 적었습니다. 같은 내용을 두 곳에 쓰지 않고, 다른 문서는 원본을 가리킵니다.

| 알고 싶은 것 | 원본 |
|---|---|
| 무엇을 만드는가 (기능, 비기능 요구) | [product/PRD.md](product/PRD.md) |
| 언제 무엇을 하는가, 지금 어디까지 왔는가 | [product/ROADMAP.md](product/ROADMAP.md) (1주차의 "진행 상황") |
| 누가 무엇을 승인하는가, 지표, 브랜치와 PR 규칙 | [product/PLAYBOOK.md](product/PLAYBOOK.md) |
| 단계별로 무엇을 어떻게 구현하는가 | [specs/](specs/README.md) (스펙마다 `README.md` 색인) |
| 결정 대기 항목 | 각 스펙의 색인과 `requirements.md` "결정 대기 항목", `design.md` "요구사항 공백" |
| 확정한 아키텍처 결정 | [adr/](adr/README.md) |
| 실행해서 확인한 기술 사실 | [spikes/](spikes/) |
| Claude Code 설정이 무엇을 강제하고 어디까지 막는가 | [guide/claude-code-setup.md](guide/claude-code-setup.md) |
| 팀 방법론 (일반 가이드. PR Lens 전용이 아님) | [guide/claude-code-team-methodology.md](guide/claude-code-team-methodology.md) |
| Claude에게 주는 지시 | 루트 `CLAUDE.md`, `backend/CLAUDE.md`, `web/CLAUDE.md`, `.claude/rules/` |

## 임시 문서

- [spec-review/](spec-review/README.md): P2·P3 스펙의 검토 결과입니다. P2와 P3 스펙을 고친 뒤 폴더째 지웁니다. P1 스펙이 출처로 가리키는 05번의 내용은 지우기 전에 P1 설계 문서로 옮기거나 출처 표기를 고칩니다.

## 문서를 고칠 때

- 진행 상황은 ROADMAP에만 적습니다. 인수인계용 문서를 따로 만들지 않습니다.
- 결정이 확정되면 ADR을 쓰고(`/adr`), 스펙의 결정 대기 표에서 그 항목을 "확정"으로 바꾸고 ADR을 가리킵니다.
- 스펙을 고치면 그 스펙 폴더의 색인(`README.md`)도 함께 고칩니다.

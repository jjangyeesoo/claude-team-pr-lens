## 요약

[1/3] backend/src/main/java/com/example/report/AuditReportBuilder.java
감사 리포트 빌더입니다. 합계 계산에 넘침 위험이 있습니다.

[2/3] backend/src/main/java/com/example/report/BillingReportBuilder.java
청구 리포트 빌더입니다. 반복되는 메서드가 많습니다.

[3/3] backend/src/main/java/com/example/report/UsageReportBuilder.java
사용량 리포트 빌더입니다. 큰 문제는 없습니다.

## 심각도별 지적 수

- blocker: 0
- major: 1
- minor: 1
- nit: 1

강등된 지적: 0건

## 파일별 지적 (3건)

### backend/src/main/java/com/example/report/AuditReportBuilder.java

- `L33` **major** · correctness
  합계를 int로 더하면 값이 클 때 넘칩니다. 설계 문서는 합계를 long으로 계산하도록 정했습니다.
  제안: long sum = 0;
  근거: spec `docs/specs/report/design.md`
### backend/src/main/java/com/example/report/BillingReportBuilder.java

- `L7` **minor** · design
  total0부터 total47까지 같은 모양의 메서드가 반복됩니다. 값 하나를 인자로 받는 메서드 하나로 합칠 수 있습니다.
  근거: general
### backend/src/main/java/com/example/report/UsageReportBuilder.java

- `L5` **nit** · convention
  클래스에 설명 주석이 없습니다.
  근거: rule `.claude/rules/backend/java-style.md`

## 라인 밖 지적 (0건)

## 제외 파일 (0건)

## 사용된 컨텍스트 파일 (3)

### claude_md

- `CLAUDE.md`

### rule

- `.claude/rules/backend/java-style.md`

### spec

- `docs/specs/report/design.md`

## 토큰 사용량과 추정 비용

- 모델: claude-opus-5-5
- 입력 토큰: 29400
- 출력 토큰: 1150
- 캐시 쓰기 토큰: 7200
- 캐시 읽기 토큰: 14400
- 추정 비용: $0.1794

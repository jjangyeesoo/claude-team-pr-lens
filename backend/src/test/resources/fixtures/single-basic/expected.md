## 요약

주문 합계 계산을 추가한 변경입니다. 수량 검증이 빠져 있고 경계값 테스트가 부족합니다.

## 심각도별 지적 수

- blocker: 0
- major: 2
- minor: 1
- nit: 1

강등된 지적: 1건

## 파일별 지적 (3건)

### backend/src/main/java/com/example/order/OrderService.java

- `L42` **major** · correctness
  수량이 음수이면 합계가 줄어듭니다. 합계를 계산하기 전에 수량이 1 이상인지 확인해야 합니다.
  제안: if (item.quantity() < 1) { throw new IllegalArgumentException("quantity"); }
  근거: general
- `L44` **minor** · convention
  생성자 인자가 세 개로 늘었습니다. 프로젝트 스타일은 인자가 많아지면 이름 있는 생성 메서드를 쓰도록 합니다.
  근거: rule `.claude/rules/backend/java-style.md`
### backend/src/test/java/com/example/order/OrderServiceTest.java

- `L14` **nit** · convention
  테스트의 객체 생성 줄이 길어 읽기 어렵습니다. 변수로 나누면 좋겠습니다.
  근거: general

## 라인 밖 지적 (1건)

- `backend/src/main/java/com/example/order/OrderService.java` (사유: line_missing) **major** · test
  합계 계산의 경계값(빈 목록, 수량 0) 테스트가 없습니다.
  근거: general

## 제외 파일 (0건)

## 사용된 컨텍스트 파일 (2)

### claude_md

- `CLAUDE.md`

### rule

- `.claude/rules/backend/java-style.md`

## 토큰 사용량과 추정 비용

- 모델: claude-opus-5-5
- 입력 토큰: 5200
- 출력 토큰: 640
- 캐시 쓰기 토큰: 4100
- 캐시 읽기 토큰: 0
- 추정 비용: $0.0541

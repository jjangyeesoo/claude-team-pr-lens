# 0004. CLI 라이브러리로 picocli를 쓴다
- 상태: accepted · 날짜: 2026-10-08

## 배경
P1은 `prlens review <PR URL> [--format markdown|json] [--config <path>]` 한 번 실행하고 끝나는 CLI다(PRD FR-1~6). PRD 7장은 CLI를 backend와 같은 코드베이스에 두기로 하고 라이브러리를 "picocli 또는 Spring Shell"로 남겼다(PRD 10장, P1 요구사항의 결정 대기 D-1).

이 결정이 필요한 이유는 둘이다.

- P1 작업 1.2가 CLI 라이브러리를 `build.gradle.kts`에 버전 고정으로 추가한다. 의존성 추가 전에 정해야 한다.
- CLI의 종료 코드는 계약이다(blocker 1, 불완전 결과 3, 사용법·설정 오류 2 등. P1 설계 "예외 계층과 종료 코드"). P2의 GitHub Actions 실행도 이 값을 쓴다. 인자 오류를 포함한 모든 오류가 한 곳(`CliPipeline`)의 종료 코드 결정을 거쳐야 한다.

확인한 사실(`docs/spikes/2026-10-04-build-stack.md`, 한 대의 Windows 실행 결과):

- picocli 4.7.7이 Spring Boot 4.1.1, JUnit 6.0.3, jqwik 1.10.1, ArchUnit 1.5.1, Anthropic Java SDK 2.68.0과 함께 의존성으로 해석된다.
- `springBoot { mainClass = ... }`로 CLI 진입점을 지정한 실행 jar는 Spring 없이 실행되고 약 0.4초에 끝났다.
- main 클래스가 둘이면(`@SpringBootApplication` 클래스와 CLI 진입점) 실행 jar의 기본 시작 클래스는 Spring 쪽이 되어 `java -jar`가 웹 서버를 띄운다.

## 결정
CLI 명령 해석에 picocli를 쓴다.

- 버전은 4.7.7로 고정한다(`info.picocli:picocli`). 추가는 P1 작업 1.2에서 한다.
- CLI는 Spring 컨텍스트를 띄우지 않는다. `PrLensMain`이 picocli를 직접 실행하고 `CliPipeline`이 객체를 직접 조립한다. `picocli-spring-boot-starter`는 쓰지 않는다.
- picocli 타입은 `cli` 패키지에서만 쓴다. `review`는 `cli`에 의존하지 않는다(P1 요구사항 17.3, ArchUnit 규칙).
- picocli의 자동 오류 처리는 끄고, 인자 오류는 `UsageException`으로 바꿔 `CliPipeline`의 종료 코드 결정으로 보낸다(P1 작업 18.1, 18.5).
- 실행 jar의 시작 클래스는 `springBoot { mainClass = ... }`로 CLI 진입점을 지정한다(P1 작업 18.7).

## 고려한 대안
- **Spring Shell**: Spring Boot와 같은 생태계라 빈 주입과 설정 바인딩을 그대로 쓴다. 하지만 대화형 셸이 중심이라 단발 실행 CLI에는 맞지 않고, 실행할 때마다 Spring 컨텍스트를 띄워야 해서 시작이 느리며, 종료 코드를 프레임워크의 처리 흐름에 맞춰 넣어야 한다.
- **picocli + `picocli-spring-boot-starter`**: 명령 클래스에 빈을 주입받을 수 있다. 하지만 Spring 컨텍스트를 띄우는 비용이 그대로 들고, P1의 CLI가 조립하는 객체는 `CliPipeline` 한 곳에서 직접 만들 수 있는 양이다.
- **라이브러리 없이 직접 파싱**: 의존성이 늘지 않는다. 하지만 명령이 하나뿐이어도 옵션 검증, 사용법 출력, 오류 메시지를 직접 만들어야 하고, P2에서 명령이나 옵션이 늘면 다시 정해야 한다.

## 결과
- 의존성이 하나 늘어난다. 추가에는 리뷰어의 승인과 이유 설명이 필요하다(PLAYBOOK 승인 규칙). 이유는 이 문서를 가리킨다.
- Spring의 의존성 주입과 설정 바인딩을 CLI에서 쓰지 못한다. 객체 조립은 `CliPipeline`이, 설정 읽기는 `ConfigLoader`가 직접 한다.
- 서버(P2)와 CLI가 한 jar에 있으므로 시작 클래스를 명시해야 한다. 빠뜨리면 `java -jar`가 웹 서버를 띄운다.
- 명령 해석이 `cli` 패키지에만 있어, 이 결정을 바꾸더라도 고칠 곳은 `cli`뿐이다.
- 후속 작업
  - P1 작업 1.2: 의존성 추가.
  - P1 작업 18.1, 18.5: 명령 정의와 예외 → 종료 코드 매핑.
  - P1 작업 18.7: 시작 클래스 지정과 실행 패키징.
- 아직 확인하지 않은 것
  - picocli의 자동 오류 처리를 끄는 방법(`setParameterExceptionHandler`)은 문서 요약으로만 확인했다(`docs/spec-review/05-external-facts.md`). 작업 18.1에서 실제로 확인한다.
  - macOS와 Linux에서의 실행. P1 작업 21의 CI 매트릭스에서 확인한다.

# Spike: P1 의존성 조합과 실행 jar (2026-10-04)

P1 작업 1.2가 추가할 의존성이 스타터의 Spring Boot 4.1.1, JUnit 6과 함께 도는지 확인했습니다. 저장소 밖 임시 Gradle 프로젝트에서 실행했고, 저장소의 `build.gradle.kts`는 고치지 않았습니다.

- 환경: Windows 11, JDK Temurin 17, Gradle 9.7.1(스타터의 wrapper), Spring Boot 4.1.1
- 한 사람(한 대)의 실행 결과입니다. macOS와 Linux, IDE 테스트 러너는 확인하지 않았습니다.

## 결과

| 질문 | 결과 |
|---|---|
| jqwik이 JUnit Platform 6에서 도는가 | 돕니다. jqwik 1.10.1 + JUnit 6.0.3. 같은 클래스의 Jupiter `@Test`와 jqwik `@Property(tries = 200)`가 함께 실행됐고, 일부러 틀리게 쓴 속성은 실패하며 반례가 축소돼 보고됐습니다. jqwik이 선언한 `junit-platform-engine` 1.14.4는 Spring Boot BOM이 6.0.3으로 올립니다 |
| ArchUnit이 JUnit 6에서 도는가 | 돕니다. `archunit-junit5` 1.5.1의 `@ArchTest`가 실행됐습니다 |
| ArchUnit은 대상 클래스가 없는 규칙을 어떻게 처리하는가 | 기본값에서는 실패합니다("failed to check any classes"). 규칙에 `allowEmptyShould(true)`를 붙이면 통과합니다. 패키지 뼈대만 있는 시점의 규칙에 필요합니다 |
| picocli, Anthropic Java SDK가 함께 해석되는가 | 해석됩니다. picocli 4.7.7, anthropic-java 2.68.0. SDK를 실제로 호출하지는 않았습니다 |
| YAML 파서와 Jackson | 새로 추가할 것이 없습니다. snakeyaml 2.6과 Jackson 3.1.5(`tools.jackson`)가 이미 들어와 있습니다. Anthropic SDK는 Jackson 2(`com.fasterxml.jackson.databind` 2.21.5)도 함께 끌어오므로 import를 잘못 고르지 않게 ArchUnit 규칙으로 막습니다 |
| 실행 jar의 시작 클래스 | main 클래스가 둘이면(`@SpringBootApplication` 클래스와 CLI 진입점) 기본 시작 클래스는 Spring 쪽이 되어 `java -jar`가 웹 서버를 띄웁니다. `springBoot { mainClass = "..." }`로 CLI 진입점을 지정하면 Spring 없이 실행되고 약 0.4초에 끝났습니다. jar 크기는 약 66MB입니다 |

## 사용한 의존성 선언

```kotlin
implementation("info.picocli:picocli:4.7.7")
implementation("com.anthropic:anthropic-java:2.68.0")
testImplementation("net.jqwik:jqwik:1.10.1")
testImplementation("com.tngtech.archunit:archunit-junit5:1.5.1")
```

## 남은 확인

- Anthropic Java SDK의 실제 호출: 구조화된 출력, effort, `cache_control` 빌더, usage 접근자, 오류 응답의 `retry-after` 접근. P1 작업 13.4(실제 Claude API 스모크)에서 확인합니다.
- 속성 44개를 jqwik 기본 1,000회로 돌렸을 때 Stop hook의 제한 시간(540초) 안에 드는지.

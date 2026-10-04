plugins {
	java
	id("org.springframework.boot") version "4.1.1"
	id("io.spring.dependency-management") version "1.1.7"
	id("com.diffplug.spotless") version "8.10.3"
}

group = "com.example"
version = "0.0.1-SNAPSHOT"

java {
	toolchain {
		languageVersion = JavaLanguageVersion.of(17)
	}
}

repositories {
	mavenCentral()
}

dependencies {
	implementation("org.springframework.boot:spring-boot-starter-validation")
	implementation("org.springframework.boot:spring-boot-starter-webmvc")
	testImplementation("org.springframework.boot:spring-boot-starter-validation-test")
	testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<Test> {
	useJUnitPlatform()
	// 실패한 테스트 이름, 기대값/실제값, 위치를 콘솔에 출력한다 (Stop hook이 이 로그를 Claude에게 전달).
	testLogging {
		events("failed")
		exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
		showCauses = true
		showStackTraces = true
	}
}

// 팀 공통 포맷 규칙. Claude Code의 Stop hook(.claude/hooks/VerifyOnStop.java)이 spotlessApply를 실행한다.
spotless {
	// 기존 코드베이스에 도입할 때는 아래 줄의 주석을 풀어, 기준 브랜치 이후 바뀐 파일만 포맷한다.
	// (그렇지 않으면 첫 Stop hook 실행에서 모든 파일이 재포맷되어 거대한 diff가 생긴다)
	// ratchetFrom("origin/main")
	java {
		target("src/**/*.java")
		googleJavaFormat()
		removeUnusedImports()
	}
}

package com.prlens;

import static com.tngtech.archunit.core.domain.JavaAccess.Predicates.target;
import static com.tngtech.archunit.core.domain.properties.HasName.Predicates.name;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 패키지 의존 규칙 (ADR 0003, design.md "계층과 의존 방향"). 대상 클래스가 아직 없는 규칙은 {@code allowEmptyShould(true)}로
 * 둔다.
 */
@AnalyzeClasses(packages = "com.prlens", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTest {

  private static final DescribedPredicate<JavaClass> SPRING_ANNOTATIONS =
      DescribedPredicate.describe(
          "Spring 애너테이션",
          c -> c.isAnnotation() && c.getPackageName().startsWith("org.springframework"));

  @ArchTest
  static final ArchRule modelDependsOnlyOnJdk =
      classes()
          .that()
          .resideInAPackage("com.prlens.model..")
          .should()
          .onlyDependOnClassesThat()
          .resideInAnyPackage("java..", "com.prlens.model..");

  @ArchTest
  static final ArchRule reviewDoesNotDependOnCliOutputGithubConfig =
      noClasses()
          .that()
          .resideInAPackage("com.prlens.review..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "com.prlens.cli..",
              "com.prlens.output..",
              "com.prlens.github..",
              "com.prlens.config..")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule reviewDoesNotReferenceAnthropicLlmClient =
      noClasses()
          .that()
          .resideInAPackage("com.prlens.review..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("com.prlens.llm.AnthropicLlmClient")
          .allowEmptyShould(true);

  @ArchTest
  static final ArchRule contextAndPullrequestDoNotReferenceHttpGitHubClient =
      noClasses()
          .that()
          .resideInAnyPackage("com.prlens.context..", "com.prlens.pullrequest..")
          .should()
          .dependOnClassesThat()
          .haveFullyQualifiedName("com.prlens.github.HttpGitHubClient")
          .allowEmptyShould(true);

  // 스타터의 common과 StarterApplication은 P2 스펙에서 위치를 정할 때까지 예외로 둔다 (ADR 0003).
  @ArchTest
  static final ArchRule springAnnotationsOnlyInCli =
      noClasses()
          .that()
          .resideOutsideOfPackages("com.prlens.cli..", "com.prlens.common..")
          .and()
          .doNotHaveFullyQualifiedName("com.prlens.StarterApplication")
          .should()
          .dependOnClassesThat(SPRING_ANNOTATIONS)
          .allowEmptyShould(true);

  // ADR 0004
  @ArchTest
  static final ArchRule picocliOnlyInCli =
      noClasses()
          .that()
          .resideOutsideOfPackage("com.prlens.cli..")
          .should()
          .dependOnClassesThat()
          .resideInAPackage("picocli..");

  @ArchTest
  static final ArchRule noPrintln = noClasses().should().accessTargetWhere(target(name("println")));

  // Anthropic SDK가 Jackson 2도 끌어온다. Jackson 3(tools.jackson)만 쓴다.
  @ArchTest
  static final ArchRule noJackson2Databind =
      noClasses()
          .should()
          .dependOnClassesThat()
          .resideInAPackage("com.fasterxml.jackson.databind..");
}

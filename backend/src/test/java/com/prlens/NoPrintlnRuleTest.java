package com.prlens;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import java.io.PrintStream;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;

/** {@link ArchitectureTest#noPrintln}이 호출과 메서드 참조를 모두 잡는지 확인한다. */
class NoPrintlnRuleTest {

  @Test
  void noPrintlnRejectsDirectCall() {
    assertViolation(DirectCall.class);
  }

  @Test
  void noPrintlnRejectsMethodReference() {
    assertViolation(MethodReference.class);
  }

  private static void assertViolation(Class<?> fixture) {
    JavaClasses classes = new ClassFileImporter().importClasses(fixture);

    assertThatThrownBy(() -> ArchitectureTest.noPrintln.check(classes))
        .isInstanceOf(AssertionError.class)
        .hasMessageContaining("println");
  }

  static class DirectCall {
    void print(PrintStream out) {
      out.println("x");
    }
  }

  static class MethodReference {
    void print(PrintStream out, List<String> lines) {
      Consumer<String> printer = out::println;
      lines.forEach(printer);
    }
  }
}

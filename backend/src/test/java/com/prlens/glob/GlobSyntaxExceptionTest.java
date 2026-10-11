package com.prlens.glob;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.support.PrLensException;
import org.junit.jupiter.api.Test;

/** `ConfigLoader`가 잡아서 항목 이름과 패턴을 보고하는 예외 (요구사항 3.11). */
class GlobSyntaxExceptionTest {

  @Test
  void carriesPatternAndReason() {
    GlobSyntaxException exception = new GlobSyntaxException("src/{a,b", "중괄호가 닫히지 않았습니다");

    assertThat(exception).isInstanceOf(PrLensException.class);
    assertThat(exception.pattern()).isEqualTo("src/{a,b");
    assertThat(exception.reason()).isEqualTo("중괄호가 닫히지 않았습니다");
    assertThat(exception).hasMessageContaining("src/{a,b").hasMessageContaining("중괄호가 닫히지 않았습니다");
  }

  @Test
  void validateDeclaresItInItsContract() throws Exception {
    assertThat(GlobMatcher.class.getMethod("validate", String.class).getExceptionTypes())
        .containsExactly(GlobSyntaxException.class);
  }
}

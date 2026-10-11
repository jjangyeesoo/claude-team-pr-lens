package com.prlens.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 팀 컨텍스트 타입 (요구사항 17.4~17.8, 경로 유일성). */
class ReviewContextTest {

  private static ContextFile file(String path, ContextSource source) {
    return new ContextFile(path, "내용", source, Revision.BASE);
  }

  @Test
  void duplicatePathIsRejected() {
    List<ContextFile> files =
        List.of(
            file("CLAUDE.md", ContextSource.CLAUDE_MD), file("CLAUDE.md", ContextSource.IMPORT));

    assertThatIllegalArgumentException()
        .isThrownBy(() -> new ReviewContext(files))
        .withMessageContaining("CLAUDE.md");
  }

  @Test
  void filesAreCopiedAndUnmodifiable() {
    ContextFile root = file("CLAUDE.md", ContextSource.CLAUDE_MD);
    List<ContextFile> source = new ArrayList<>(List.of(root));
    ReviewContext context = new ReviewContext(source);

    source.add(file("backend/CLAUDE.md", ContextSource.CLAUDE_MD));

    assertThat(context.files()).containsExactly(root);
    assertThatThrownBy(() -> context.files().add(root))
        .isInstanceOf(UnsupportedOperationException.class);
  }

  @Test
  void nullRequiredFieldIsRejectedWithFieldName() {
    assertThatNullPointerException().isThrownBy(() -> new ReviewContext(null)).withMessage("files");
    assertThatNullPointerException()
        .isThrownBy(() -> new ContextFile(null, "c", ContextSource.RULE, Revision.BASE))
        .withMessage("path");
    assertThatNullPointerException()
        .isThrownBy(() -> new ContextFile("p", null, ContextSource.RULE, Revision.BASE))
        .withMessage("content");
    assertThatNullPointerException()
        .isThrownBy(() -> new ContextFile("p", "c", null, Revision.BASE))
        .withMessage("source");
    assertThatNullPointerException()
        .isThrownBy(() -> new ContextFile("p", "c", ContextSource.RULE, null))
        .withMessage("revision");
  }

  @Test
  void sourceDeclarationOrderIsPriorityOrder() {
    assertThat(ContextSource.values())
        .containsExactly(
            ContextSource.CLAUDE_MD, ContextSource.RULE, ContextSource.IMPORT, ContextSource.SPEC);
  }

  @Test
  void instancesWithSameFieldValuesAreEqual() {
    ReviewContext one =
        new ReviewContext(new ArrayList<>(List.of(file("a.md", ContextSource.RULE))));
    ReviewContext other = new ReviewContext(List.of(file("a.md", ContextSource.RULE)));

    assertThat(one).isEqualTo(other).hasSameHashCodeAs(other);
  }
}

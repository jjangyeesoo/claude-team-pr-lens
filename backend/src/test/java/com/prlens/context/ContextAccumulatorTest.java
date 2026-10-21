package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.Revision;
import org.junit.jupiter.api.Test;

/** {@link ContextAccumulator} (요구사항 4.8, 6.7, 8.2, 8.5). */
class ContextAccumulatorTest {

  private static ContextFile file(String path, String content, ContextSource source) {
    return new ContextFile(path, content, source, Revision.BASE);
  }

  @Test
  void addNormalizesPathsAndMergesEquivalentOnes() {
    ContextAccumulator acc = new ContextAccumulator();

    acc.add(file("./a.md", "import", ContextSource.IMPORT));
    acc.add(file("/a.md", "spec", ContextSource.SPEC));
    acc.add(file("sub\\b.md", "rule", ContextSource.RULE));

    assertThat(acc.files())
        .extracting(ContextFile::path, ContextFile::source)
        .containsExactly(
            tuple("a.md", ContextSource.IMPORT), tuple("sub/b.md", ContextSource.RULE));
    assertThat(acc.paths()).containsExactlyInAnyOrder("a.md", "sub/b.md");
  }

  @Test
  void addKeepsHigherPrioritySourceRegardlessOfOrder() {
    ContextAccumulator acc = new ContextAccumulator();

    acc.add(file("x.md", "first", ContextSource.IMPORT));
    acc.add(file("a.md", "spec", ContextSource.SPEC));
    acc.add(file("y.md", "second", ContextSource.IMPORT));
    acc.add(file("./a.md", "claude", ContextSource.CLAUDE_MD));
    acc.add(file("y.md", "later same", ContextSource.IMPORT));

    assertThat(acc.files())
        .extracting(ContextFile::path, ContextFile::content, ContextFile::source)
        .containsExactly(
            tuple("x.md", "first", ContextSource.IMPORT),
            tuple("a.md", "claude", ContextSource.CLAUDE_MD),
            tuple("y.md", "second", ContextSource.IMPORT));
  }

  @Test
  void toContextHasNoDuplicatePaths() {
    ContextAccumulator acc = new ContextAccumulator();

    acc.add(file("a.md", "1", ContextSource.SPEC));
    acc.add(file("./a.md", "2", ContextSource.SPEC));
    acc.add(file("/a.md", "3", ContextSource.IMPORT));
    acc.add(file(".\\a.md", "4", ContextSource.RULE));

    assertThat(acc.toContext().files()).hasSize(1);
    assertThat(acc.toContext().files().get(0).source()).isEqualTo(ContextSource.RULE);
  }
}

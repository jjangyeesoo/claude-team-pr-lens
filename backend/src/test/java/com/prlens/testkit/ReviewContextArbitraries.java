package com.prlens.testkit;

import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.ReviewContext;
import com.prlens.model.Revision;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;

/** {@code ReviewContext} 생성기. 경로가 겹치지 않는 파일 0~5개를 낸다. */
public final class ReviewContextArbitraries {

  private ReviewContextArbitraries() {}

  public static Arbitrary<ReviewContext> contexts() {
    return contextFiles()
        .list()
        .uniqueElements(ContextFile::path)
        .ofMinSize(0)
        .ofMaxSize(5)
        .map(ReviewContext::new);
  }

  public static Arbitrary<ContextFile> contextFiles() {
    return Combinators.combine(
            RepoPathArbitraries.normalized(),
            ReviewResultArbitraries.texts(),
            Arbitraries.of(ContextSource.class),
            Arbitraries.of(Revision.class))
        .as(ContextFile::new);
  }
}

package com.prlens.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

/** 공유 타입은 record, enum, sealed interface로만 정의한다 (요구사항 17.4). */
class ModelTypesShapeTest {

  private static final List<Class<?>> SHARED_TYPES =
      List.of(
          RepoRef.class,
          PullRequestSnapshot.class,
          ChangedFile.class,
          FileStatus.class,
          PatchContent.class,
          PatchContent.Parsed.class,
          PatchContent.Absent.class,
          PatchContent.Unparseable.class,
          Hunk.class,
          DiffLine.class,
          LineKind.class,
          ReviewContext.class,
          ContextFile.class,
          ContextSource.class,
          Revision.class,
          ReviewResult.class,
          ExcludedFile.class,
          ResultStatus.class,
          IncompleteReason.class,
          IncompleteDetails.class,
          ChunkIssue.class,
          FileCountGap.class,
          ReviewStats.class,
          ReviewMode.class,
          Finding.class,
          Basis.class,
          Demotion.class,
          Severity.class,
          Category.class,
          BasisType.class,
          LineVerdict.class,
          SummaryOnlyReason.class,
          Usage.class,
          Configuration.class,
          ModelPricing.class);

  @Test
  void sharedTypesAreRecordsEnumsOrSealedInterfaces() {
    assertThat(SHARED_TYPES)
        .allMatch(
            type -> type.isRecord() || type.isEnum() || (type.isInterface() && type.isSealed()));
  }

  @Test
  void patchContentPermitsExactlyThreeVariants() {
    assertThat(PatchContent.class.getPermittedSubclasses())
        .containsExactlyInAnyOrder(
            PatchContent.Parsed.class, PatchContent.Absent.class, PatchContent.Unparseable.class);
  }
}

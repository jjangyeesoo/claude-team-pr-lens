package com.prlens.testkit;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.ChangedFile;
import com.prlens.model.ContextSource;
import com.prlens.model.PatchContent;
import com.prlens.model.Revision;
import com.prlens.testkit.FixtureLoader.Fixture;
import org.junit.jupiter.api.Test;

class FixtureLoaderTest {

  @Test
  void snapshotParsesPatchIntoHunks() {
    Fixture fixture = FixtureLoader.load("single-basic");

    assertThat(fixture.snapshot().files()).isNotEmpty();
    assertThat(fixture.snapshot().files())
        .extracting(ChangedFile::patch)
        .allSatisfy(patch -> assertThat(patch).isInstanceOf(PatchContent.Parsed.class));
    PatchContent.Parsed parsed = (PatchContent.Parsed) fixture.snapshot().files().get(0).patch();
    assertThat(parsed.hunks()).isNotEmpty();
  }

  @Test
  void nullPatchBecomesAbsent() {
    Fixture fixture = FixtureLoader.load("excluded-files");

    assertThat(fixture.snapshot().files())
        .anySatisfy(file -> assertThat(file.patch()).isInstanceOf(PatchContent.Absent.class));
  }

  @Test
  void contextFilesKeepSourceAndRevision() {
    Fixture fixture = FixtureLoader.load("single-basic");

    assertThat(fixture.context().files())
        .anySatisfy(
            file -> {
              assertThat(file.source()).isEqualTo(ContextSource.CLAUDE_MD);
              assertThat(file.revision()).isEqualTo(Revision.BASE);
            });
  }

  @Test
  void llmResponsesLoadInFileNameOrder() {
    Fixture fixture = FixtureLoader.load("split-three-chunks");

    assertThat(fixture.llmResponses()).hasSizeGreaterThanOrEqualTo(2);
    assertThat(fixture.llmResponses().get(0).stopReason()).isEqualTo("end_turn");
    assertThat(fixture.llmResponses().get(0).text()).startsWith("{").contains("\"findings\"");
    assertThat(fixture.llmResponses().get(0).usage().inputTokens()).isPositive();
  }
}

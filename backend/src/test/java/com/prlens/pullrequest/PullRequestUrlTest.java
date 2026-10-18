package com.prlens.pullrequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.RepoRef;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PullRequestUrlTest {

  private static final String BASE = "https://github.com/o/r/pull/1";

  @Test
  void parsesOwnerRepoAndNumber() {
    var parsed = PullRequestUrl.parse("https://github.com/octo-org/my.repo_1/pull/42");

    assertThat(parsed).isPresent();
    assertThat(parsed.get().repo()).isEqualTo(new RepoRef("octo-org", "my.repo_1"));
    assertThat(parsed.get().number()).isEqualTo(42);
  }

  @Test
  void acceptsNameLengthBoundaries() {
    String hundred = "a".repeat(100);
    String hundredOne = "a".repeat(101);

    var shortest = PullRequestUrl.parse("https://github.com/o/r/pull/1");
    assertThat(shortest).isPresent();
    assertThat(shortest.get().repo()).isEqualTo(new RepoRef("o", "r"));

    var longest = PullRequestUrl.parse("https://github.com/" + hundred + "/" + hundred + "/pull/1");
    assertThat(longest).isPresent();
    assertThat(longest.get().repo()).isEqualTo(new RepoRef(hundred, hundred));

    assertThat(PullRequestUrl.parse(BASE)).isPresent();
    assertThat(PullRequestUrl.parse("https://github.com/" + hundredOne + "/r/pull/1")).isEmpty();
    assertThat(PullRequestUrl.parse("https://github.com/o/" + hundredOne + "/pull/1")).isEmpty();
  }

  @Test
  void acceptsNumberUpperBound() {
    var parsed = PullRequestUrl.parse("https://github.com/o/r/pull/2147483647");

    assertThat(parsed).isPresent();
    assertThat(parsed.get().number()).isEqualTo(Integer.MAX_VALUE);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {"/", "/files", "/commits", "?diff=split", "#discussion_r1", "/files?w=1#x"})
  void ignoresTrailingSuffixes(String suffix) {
    var parsed = PullRequestUrl.parse("https://github.com/octo/repo/pull/7" + suffix);

    assertThat(parsed).isPresent();
    assertThat(parsed.get().repo()).isEqualTo(new RepoRef("octo", "repo"));
    assertThat(parsed.get().number()).isEqualTo(7);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "http://github.com/o/r/pull/1",
        "https://www.github.com/o/r/pull/1",
        "https://GitHub.com/o/r/pull/1",
        "https://gitlab.com/o/r/pull/1",
        "https://github.com/o/r/pulls/1",
        "https://github.com/o/r/issues/1",
        "https://github.com/o/r/pull/0",
        "https://github.com/o/r/pull/01",
        "https://github.com/o/r/pull/2147483648",
        "https://github.com/o/r/pull/9999999999",
        "https://github.com/o/r/pull/12345678901",
        "https://github.com/o/x/r/pull/1",
        "https://github.com/o x/r/pull/1",
        "https://github.com/o~x/r/pull/1",
        "https://github.com/o/r/pull/1/files/",
        "https://github.com/o/r/pull/1/diff",
        "https://github.com/o/r/pull/1\n",
        ""
      })
  void rejectsMalformedUrls(String url) {
    assertThat(PullRequestUrl.parse(BASE)).isPresent();

    assertThat(PullRequestUrl.parse(url)).isEmpty();
  }
}

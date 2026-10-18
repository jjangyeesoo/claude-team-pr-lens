package com.prlens.github;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.RepoRef;
import org.junit.jupiter.api.Test;

class PatCredentialsTest {

  @Test
  void patCredentialsBuildsBearerHeaderAndHidesTokenInToString() {
    PatCredentials credentials = new PatCredentials("ghp_fakeToken123");

    assertThat(credentials.authorizationHeader(new RepoRef("o", "r")))
        .isEqualTo("Bearer ghp_fakeToken123");
    assertThat(credentials.toString())
        .doesNotContain("ghp_fakeToken123")
        .isEqualTo("PatCredentials[***]");
  }
}

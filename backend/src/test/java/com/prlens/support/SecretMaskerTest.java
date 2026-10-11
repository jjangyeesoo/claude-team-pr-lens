package com.prlens.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/** `SecretMasker` (design.md "비밀정보 마스킹", 요구사항 19.4, 19.5, 19.7). 비밀값은 모두 가짜다. */
class SecretMaskerTest {

  private static final String TOKEN = "ghp_token123";
  private static final String API_KEY = "sk-ant-key456";

  private final SecretMasker masker = new SecretMasker(List.of(TOKEN, API_KEY));

  @Test
  void maskReplacesEachSecretWithThreeAsterisks() {
    assertThat(masker.mask("token=" + TOKEN + " end")).isEqualTo("token=*** end");
    assertThat(masker.mask(API_KEY)).isEqualTo("***");
  }

  @Test
  void maskHandlesSeveralSecretsInOneText() {
    assertThat(masker.mask("a " + TOKEN + " b " + API_KEY + " c " + TOKEN))
        .isEqualTo("a *** b *** c ***");
  }

  @Test
  void textWithoutSecretIsUnchanged() {
    assertThat(masker.mask("ghp_token12 는 비밀값이 아니다\n")).isEqualTo("ghp_token12 는 비밀값이 아니다\n");
    assertThat(masker.mask("")).isEmpty();
  }

  @Test
  void maskCoversRepeatedAdjacentAndOverlappingOccurrences() {
    SecretMasker aba = new SecretMasker(List.of("aba"));

    assertThat(aba.mask("aba-aba")).isEqualTo("***-***");
    assertThat(aba.mask("abaaba")).isEqualTo("***");
    assertThat(aba.mask("xababay")).isEqualTo("x***y");
  }

  @Test
  void contiguousCoveredRunBecomesOneMarker() {
    SecretMasker two = new SecretMasker(List.of("abc", "cde"));

    assertThat(two.mask("xabcdey")).isEqualTo("x***y");
    assertThat(two.mask("abccde")).isEqualTo("***");
    assertThat(two.mask("abc cde")).isEqualTo("*** ***");
  }

  @Test
  void emptyAndBlankSecretsAreIgnored() {
    SecretMasker withBlanks = new SecretMasker(Arrays.asList("", "  ", null, "s3cret"));

    assertThat(withBlanks.mask("a  b s3cret")).isEqualTo("a  b ***");
    assertThat(new SecretMasker(List.of()).mask("그대로")).isEqualTo("그대로");
  }

  @Test
  void toStringDoesNotExposeSecrets() {
    assertThat(masker.toString()).doesNotContain(TOKEN).doesNotContain(API_KEY);
  }

  @Test
  void sensitiveHeaderValueIsMaskedIgnoringNameCase() {
    assertThat(masker.maskHeader("Authorization", "Bearer 다른-값")).isEqualTo("***");
    assertThat(masker.maskHeader("AUTHORIZATION", "Bearer " + TOKEN)).isEqualTo("***");
    assertThat(masker.maskHeader("x-api-key", API_KEY)).isEqualTo("***");
    assertThat(masker.maskHeader("X-Api-Key", "다른-값")).isEqualTo("***");
  }

  @Test
  void otherHeaderValueIsMaskedOnlyWhereSecretAppears() {
    assertThat(masker.maskHeader("Accept", "application/json")).isEqualTo("application/json");
    assertThat(masker.maskHeader("X-Debug", "t=" + TOKEN)).isEqualTo("t=***");
  }
}

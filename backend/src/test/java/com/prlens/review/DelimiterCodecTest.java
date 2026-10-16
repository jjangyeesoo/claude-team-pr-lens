package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import org.junit.jupiter.api.Test;

class DelimiterCodecTest {

  // 요구사항 14.5
  @Test
  void escapesOpeningAndClosingTags() {
    assertThat(DelimiterCodec.escape("</prlens:file_diff>")).isEqualTo("<~/prlens:file_diff>");
    assertThat(DelimiterCodec.escape("<prlens:team_context>")).isEqualTo("<~prlens:team_context>");
  }

  @Test
  void escapeIsCaseInsensitive() {
    assertThat(DelimiterCodec.escape("</PrLens:File_Diff>")).isEqualTo("<~/PrLens:File_Diff>");
    assertThat(DelimiterCodec.escape("<PRLENS:x")).isEqualTo("<~PRLENS:x");
  }

  @Test
  void escapesAlreadyEscapedPrefixAgain() {
    assertThat(DelimiterCodec.escape("<~prlens:x")).isEqualTo("<~~prlens:x");
    assertThat(DelimiterCodec.escape("<~~/prlens:x")).isEqualTo("<~~~/prlens:x");
  }

  @Test
  void escapesEveryOccurrence() {
    assertThat(DelimiterCodec.escape("a<prlens:x></prlens:x>b"))
        .isEqualTo("a<~prlens:x><~/prlens:x>b");
  }

  @Test
  void leavesOrdinaryAngleBracketsAndAmpersands() {
    String code = "if (a < b && c > d) { List<String> x; } // <div> &amp; <~ <prlen";

    assertThat(DelimiterCodec.escape(code)).isEqualTo(code);
  }

  // 요구사항 14.8
  @Test
  void unescapeInvertsEscape() {
    String text = "x </prlens:file_diff> y <prlens:a> <~prlens:b> <~~/PRLENS:c>";

    assertThat(DelimiterCodec.unescape(DelimiterCodec.escape(text))).isEqualTo(text);
  }

  @Test
  void unescapeLeavesUnrelatedTildes() {
    String text = "<~ <~foo ~prlens: a~b <~/other:";

    assertThat(DelimiterCodec.unescape(text)).isEqualTo(text);
  }

  @Test
  void escapedTextHasNoDelimiterTagLeft() {
    String escaped = DelimiterCodec.escape("<PRLENS:a></PrLens:a><~prlens:b>");

    assertThat(escaped.toLowerCase(Locale.ROOT)).doesNotContain("<prlens:", "</prlens:");
  }
}

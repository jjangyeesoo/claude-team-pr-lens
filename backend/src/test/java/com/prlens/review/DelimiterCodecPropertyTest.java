package com.prlens.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

// 작업 11.4: Property 33(Delimiter 이스케이프)
class DelimiterCodecPropertyTest {

  private static final String TAG = "file_diff";

  /** 태그 모양 조각을 편향 생성한 텍스트. */
  @Provide
  Arbitrary<String> texts() {
    Arbitrary<String> fragment =
        Arbitraries.oneOf(
            Arbitraries.of(
                "<prlens:",
                "</prlens:",
                "<PrLens:",
                "</PRLENS:",
                "<~prlens:",
                "<~~/prlens:",
                "<~",
                "<",
                "~",
                "/",
                "prlens:",
                "<~/prlens:",
                "<prlens:file_diff>",
                "</prlens:file_diff>",
                "&",
                ">"),
            Arbitraries.strings().ofMaxLength(8));
    return fragment.list().ofMaxSize(12).map(parts -> String.join("", parts));
  }

  private static int count(String haystack, String needle) {
    String h = haystack.toLowerCase(Locale.ROOT);
    int count = 0;
    for (int i = h.indexOf(needle); i >= 0; i = h.indexOf(needle, i + 1)) {
      count++;
    }
    return count;
  }

  // Feature: pr-lens-p1-cli, Property 33: Delimiter 이스케이프
  @Property
  void escapedTextHasNoDelimiterTagAndRoundTrips(@ForAll("texts") String text) {
    String escaped = DelimiterCodec.escape(text);

    assertThat(count(escaped, "<prlens:")).isZero();
    assertThat(count(escaped, "</prlens:")).isZero();
    assertThat(DelimiterCodec.unescape(escaped)).isEqualTo(text);
  }

  // Feature: pr-lens-p1-cli, Property 33: Delimiter 이스케이프
  @Property
  void wrappedRegionHasExactlyOneOpeningAndOneClosingTag(@ForAll("texts") String text) {
    String open = "<prlens:" + TAG + ">";
    String close = "</prlens:" + TAG + ">";
    String region = open + DelimiterCodec.escape(text) + close;

    assertThat(count(region, "<prlens:")).isEqualTo(1);
    assertThat(count(region, "</prlens:")).isEqualTo(1);
    assertThat(List.of(region.startsWith(open), region.endsWith(close))).containsOnly(true);
  }
}

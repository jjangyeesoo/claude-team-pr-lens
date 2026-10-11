package com.prlens.support;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Tuple;

/** `SecretMasker`와 `MaskingPrintStream`의 속성 테스트 (design.md Property 39). 비밀값은 모두 생성한 가짜다. */
class SecretMaskingPropertyTest {

  /** 자기 겹침({@code aba})과 서로 부분 문자열인 비밀값이 잘 나오도록 작게 둔다. {@code *}와 공백은 뺀다(design.md G-3). */
  private static final char[] ALPHABET = {'a', 'b', 'c', '-', '_', '가', '나'};

  private enum Action {
    NONE,
    FLUSH,
    CHECK_ERROR
  }

  /** 텍스트의 {@code cut} 위치(길이로 나눈 나머지)까지 쓴 뒤 {@code action}을 한다. */
  private record Step(int cut, Action action) {}

  private record Scenario(List<String> secrets, String text, String headerValue) {}

  @Provide
  Arbitrary<Scenario> scenarios() {
    Arbitrary<List<String>> secrets =
        Arbitraries.strings()
            .withChars(ALPHABET)
            .ofMinLength(1)
            .ofMaxLength(8)
            .list()
            .ofMinSize(1)
            .ofMaxSize(2);
    return secrets.flatMap(
        chosen ->
            Combinators.combine(texts(chosen), texts(chosen))
                .as(
                    (text, headerText) ->
                        new Scenario(
                            chosen, text, headerText.replace("\n", "").replace("\r", ""))));
  }

  /** 비밀값 전체, 비밀값의 앞부분, 채움 문자열, 공백과 줄바꿈을 이어 붙인 텍스트. */
  private static Arbitrary<String> texts(List<String> secrets) {
    Arbitrary<String> whole = Arbitraries.of(secrets);
    Arbitrary<String> prefix =
        Arbitraries.of(secrets)
            .flatMap(
                secret ->
                    Arbitraries.integers()
                        .between(0, secret.length() - 1)
                        .map(length -> secret.substring(0, length)));
    Arbitrary<String> filler = Arbitraries.strings().withChars(ALPHABET).ofMaxLength(3);
    Arbitrary<String> spacing = Arbitraries.of(" ", "\n", "\r\n");
    return Arbitraries.frequencyOf(
            Tuple.of(4, whole), Tuple.of(3, prefix), Tuple.of(2, filler), Tuple.of(1, spacing))
        .list()
        .ofMaxSize(12)
        .map(pieces -> String.join("", pieces));
  }

  @Provide
  Arbitrary<List<Step>> steps() {
    return Combinators.combine(
            Arbitraries.integers().between(0, 1000), Arbitraries.of(Action.class))
        .as(Step::new)
        .list()
        .ofMaxSize(8);
  }

  /** {@code Authorization}, {@code x-api-key}의 글자별 대소문자 변형과 민감하지 않은 이름. */
  @Provide
  Arbitrary<String> headerNames() {
    Arbitrary<String> sensitive =
        Arbitraries.of("authorization", "x-api-key")
            .flatMap(
                name ->
                    Arbitraries.of(true, false)
                        .list()
                        .ofSize(name.length())
                        .map(upper -> withCase(name, upper)));
    return Arbitraries.frequencyOf(
        Tuple.of(3, sensitive), Tuple.of(1, Arbitraries.of("Accept", "X-Debug")));
  }

  private static String withCase(String name, List<Boolean> upper) {
    StringBuilder cased = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      cased.append(upper.get(i) ? Character.toUpperCase(c) : c);
    }
    return cased.toString();
  }

  // Feature: pr-lens-p1-cli, Property 39: 비밀정보 치환
  @Property
  void maskedOutputNeverContainsSecret(
      @ForAll("scenarios") Scenario scenario,
      @ForAll("steps") List<Step> steps,
      @ForAll("headerNames") String headerName) {
    List<String> secrets = scenario.secrets();
    String text = scenario.text();
    SecretMasker masker = new SecretMasker(secrets);

    String masked = masker.mask(text);
    String streamed = writeInSteps(masker, text, steps);
    String header = masker.maskHeader(headerName, scenario.headerValue());

    assertThat(secrets).noneMatch(masked::contains);
    assertThat(secrets).noneMatch(streamed::contains);
    // flush 자리에서 ***가 두 번 이어질 수 있어 접어서 비교한다. 스트림이 내용을 잃거나 중복하지 않는지 본다
    assertThat(collapseMarkers(streamed)).isEqualTo(collapseMarkers(masked));
    assertThat(secrets).noneMatch(header::contains);
    String lowerName = headerName.toLowerCase(Locale.ROOT);
    if (lowerName.equals("authorization") || lowerName.equals("x-api-key")) {
      assertThat(header).isEqualTo("***");
    }
  }

  /** 텍스트를 임의 위치에서 나눠 쓰고, 조각 사이에 flush나 checkError를 끼운 뒤 닫는다. */
  private static String writeInSteps(SecretMasker masker, String text, List<Step> steps) {
    ByteArrayOutputStream target = new ByteArrayOutputStream();
    MaskingPrintStream stream = new MaskingPrintStream(target, masker);
    List<Step> ordered =
        steps.stream()
            .map(step -> new Step(step.cut() % (text.length() + 1), step.action()))
            .sorted(Comparator.comparingInt(Step::cut))
            .toList();
    int written = 0;
    for (Step step : ordered) {
      stream.print(text.substring(written, step.cut()));
      written = step.cut();
      if (step.action() == Action.FLUSH) {
        stream.flush();
      } else if (step.action() == Action.CHECK_ERROR) {
        stream.checkError();
      }
    }
    stream.print(text.substring(written));
    stream.close();
    return target.toString(UTF_8);
  }

  private static String collapseMarkers(String text) {
    return text.replaceAll("(\\*{3})+", "***");
  }
}

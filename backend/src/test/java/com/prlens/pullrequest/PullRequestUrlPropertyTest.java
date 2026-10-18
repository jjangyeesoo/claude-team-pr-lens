package com.prlens.pullrequest;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.model.RepoRef;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.Combinators;
import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;

/** {@link PullRequestUrl#parse}의 속성 테스트 (design.md Property 1). */
class PullRequestUrlPropertyTest {

  /** 유효한 URL의 구성 요소. {@code suffix}는 경로 접미사, 쿼리, 프래그먼트를 이은 것이다. */
  record Parts(String owner, String repo, long number, String suffix) {
    String url() {
      return assemble("https", "github.com", "pull", Long.toString(number));
    }

    String assemble(String scheme, String host, String pull, String numberText) {
      return scheme
          + "://"
          + host
          + "/"
          + owner
          + "/"
          + repo
          + "/"
          + pull
          + "/"
          + numberText
          + suffix;
    }
  }

  enum MutationKind {
    SCHEME,
    HOST,
    PULL_SEGMENT,
    ZERO_NUMBER,
    LEADING_ZERO,
    NUMBER_OVERFLOW
  }

  record Mutation(MutationKind kind, String text, long overflow) {
    String apply(Parts parts) {
      String number = Long.toString(parts.number());
      return switch (kind) {
        case SCHEME -> parts.assemble("http", "github.com", "pull", number);
        case HOST -> parts.assemble("https", text, "pull", number);
        case PULL_SEGMENT -> parts.assemble("https", "github.com", text, number);
        case ZERO_NUMBER -> parts.assemble("https", "github.com", "pull", "0");
        case LEADING_ZERO -> parts.assemble("https", "github.com", "pull", "0" + number);
        case NUMBER_OVERFLOW ->
            parts.assemble("https", "github.com", "pull", Long.toString(overflow));
      };
    }
  }

  private static Arbitrary<String> name() {
    return Arbitraries.strings()
        .withCharRange('A', 'Z')
        .withCharRange('a', 'z')
        .withCharRange('0', '9')
        .withChars('_', '.', '-')
        .ofMinLength(1)
        .ofMaxLength(100);
  }

  private static Arbitrary<String> optionalPart(Arbitrary<String> body, String prefix) {
    return Arbitraries.oneOf(Arbitraries.just(""), body.map(b -> prefix + b));
  }

  private static Arbitrary<String> suffix() {
    Arbitrary<String> path = Arbitraries.of("", "/", "/files", "/commits");
    Arbitrary<String> query =
        optionalPart(
            Arbitraries.strings().withCharRange(' ', '~').excludeChars('#').ofMaxLength(20), "?");
    Arbitrary<String> fragment =
        optionalPart(Arbitraries.strings().withCharRange(' ', '~').ofMaxLength(20), "#");
    return Combinators.combine(path, query, fragment).as((p, q, f) -> p + q + f);
  }

  @Provide
  Arbitrary<Parts> validParts() {
    return Combinators.combine(
            name(), name(), Arbitraries.longs().between(1, Integer.MAX_VALUE), suffix())
        .as(Parts::new);
  }

  @Provide
  Arbitrary<Mutation> mutations() {
    return Arbitraries.of(MutationKind.class)
        .flatMap(
            kind ->
                switch (kind) {
                  case HOST ->
                      Arbitraries.of("gitlab.com", "GitHub.com", "api.github.com")
                          .map(h -> new Mutation(kind, h, 0));
                  case PULL_SEGMENT ->
                      Arbitraries.of("pulls", "issues", "pul").map(p -> new Mutation(kind, p, 0));
                  case NUMBER_OVERFLOW ->
                      Arbitraries.longs()
                          .between(Integer.MAX_VALUE + 1L, 9_999_999_999L)
                          .map(n -> new Mutation(kind, "", n));
                  default -> Arbitraries.just(new Mutation(kind, "", 0));
                });
  }

  // Feature: pr-lens-p1-cli, Property 1: PR URL 파싱 round-trip과 거부
  @Property(tries = 300)
  void roundTrip(@ForAll("validParts") Parts parts) {
    var parsed = PullRequestUrl.parse(parts.url());

    assertThat(parsed).as(parts.url()).isPresent();
    assertThat(parsed.get().repo()).isEqualTo(new RepoRef(parts.owner(), parts.repo()));
    assertThat(parsed.get().number()).isEqualTo((int) parts.number());
  }

  // Feature: pr-lens-p1-cli, Property 1: PR URL 파싱 round-trip과 거부
  @Property(tries = 300)
  void rejectsAnySingleMutation(
      @ForAll("validParts") Parts parts, @ForAll("mutations") Mutation mutation) {
    assertThat(PullRequestUrl.parse(parts.url())).as(parts.url()).isPresent();

    String mutated = mutation.apply(parts);
    assertThat(PullRequestUrl.parse(mutated)).as(mutated).isEmpty();
  }
}

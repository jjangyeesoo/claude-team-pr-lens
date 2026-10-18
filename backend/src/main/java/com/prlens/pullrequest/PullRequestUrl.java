package com.prlens.pullrequest;

import com.prlens.model.RepoRef;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * GitHub PR URL을 검증해 저장소와 PR 번호를 꺼낸다 (요구사항 1.3, 1.4, 1.9).
 *
 * <p>호스트는 정확히 {@code github.com}만 받는다. 형식이 틀리면 {@link #parse}가 빈 값을 돌려주고, 사용 오류로 바꾸는 것은 호출하는 CLI의
 * 몫이다.
 */
public final class PullRequestUrl {

  private static final Pattern PATTERN =
      Pattern.compile(
          "^https://github\\.com/([A-Za-z0-9_.-]{1,100})/([A-Za-z0-9_.-]{1,100})"
              + "/pull/([1-9][0-9]{0,9})(?:/(?:files|commits)?)?(?:\\?[^#]*)?(?:#.*)?$");

  private final RepoRef repo;
  private final int number;

  private PullRequestUrl(RepoRef repo, int number) {
    this.repo = repo;
    this.number = number;
  }

  /** URL 전체가 형식에 맞고 번호가 {@code int} 범위 안이면 값을, 아니면 빈 값을 돌려준다. */
  public static Optional<PullRequestUrl> parse(String url) {
    Objects.requireNonNull(url, "url");
    // find()는 $가 끝 줄바꿈 앞에서도 맞으므로 입력 전체를 맞추는 matches()를 쓴다.
    Matcher matcher = PATTERN.matcher(url);
    if (!matcher.matches()) {
      return Optional.empty();
    }
    long number = Long.parseLong(matcher.group(3));
    if (number > Integer.MAX_VALUE) {
      return Optional.empty();
    }
    return Optional.of(
        new PullRequestUrl(new RepoRef(matcher.group(1), matcher.group(2)), (int) number));
  }

  public RepoRef repo() {
    return repo;
  }

  public int number() {
    return number;
  }
}

package com.prlens.support;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** 출력에서 비밀값을 {@code ***}로 바꾼다 (요구사항 19.4, 19.5, 19.7). */
public final class SecretMasker {

  private static final String MARKER = "***";
  private static final Set<String> SENSITIVE_HEADERS = Set.of("authorization", "x-api-key");

  private final List<String> secrets;

  /**
   * @param secrets 비밀값 목록. null, 빈 값, 공백뿐인 값은 뺀다
   */
  public SecretMasker(Collection<String> secrets) {
    List<String> kept = new ArrayList<>();
    for (String secret : secrets) {
      if (secret != null && !secret.isBlank()) {
        kept.add(secret);
      }
    }
    this.secrets = List.copyOf(kept);
  }

  /**
   * 비밀값의 모든 출현 위치(겹침 포함)를 덮고, 연속으로 덮인 구간마다 {@code ***} 하나로 바꾼다. 비밀값에 {@code *}가 없으면 결과에 비밀값이 다시
   * 나타나지 않는다(design.md G-3).
   */
  public String mask(String text) {
    boolean[] covered = new boolean[text.length()];
    boolean found = false;
    for (String secret : secrets) {
      for (int at = text.indexOf(secret); at >= 0; at = text.indexOf(secret, at + 1)) {
        Arrays.fill(covered, at, at + secret.length(), true);
        found = true;
      }
    }
    if (!found) {
      return text;
    }
    StringBuilder masked = new StringBuilder(text.length());
    for (int i = 0; i < text.length(); i++) {
      if (!covered[i]) {
        masked.append(text.charAt(i));
      } else if (i == 0 || !covered[i - 1]) {
        masked.append(MARKER);
      }
    }
    return masked.toString();
  }

  /**
   * 줄이 끝나기 전에 내보내도 되는 앞부분의 길이. 끝이 어떤 비밀값의 앞부분과 같으면 이어지는 쓰기에서 비밀값이 완성될 수 있으므로 그 부분은 남긴다. 남기는 자리가 이미
   * 나타난 비밀값의 한가운데면 그 비밀값의 시작까지 물린다.
   */
  int safePrefixLength(String text) {
    int cut = text.length();
    for (String secret : secrets) {
      for (int n = Math.min(secret.length() - 1, text.length()); n > 0; n--) {
        if (text.regionMatches(text.length() - n, secret, 0, n)) {
          cut = Math.min(cut, text.length() - n);
          break;
        }
      }
    }
    boolean moved = true;
    while (moved) {
      moved = false;
      for (String secret : secrets) {
        for (int at = text.indexOf(secret); at >= 0; at = text.indexOf(secret, at + 1)) {
          if (at < cut && cut < at + secret.length()) {
            cut = at;
            moved = true;
          }
        }
      }
    }
    return cut;
  }

  /**
   * HTTP 디버그 로그에 쓸 헤더 값. {@code Authorization}과 {@code x-api-key}(이름 대소문자 무시)는 값 전체를 가리고, 그 밖의 헤더는
   * 값 안의 비밀값만 바꾼다.
   */
  public String maskHeader(String name, String value) {
    return SENSITIVE_HEADERS.contains(name.toLowerCase(Locale.ROOT)) ? MARKER : mask(value);
  }
}

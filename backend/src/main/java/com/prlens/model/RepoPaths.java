package com.prlens.model;

import java.util.Objects;

/** 저장소 내 경로를 Normalized_Repo_Path로 바꾼다. */
public final class RepoPaths {

  private RepoPaths() {}

  /**
   * {@code \}를 {@code /}로 바꾸고 앞의 {@code ./}와 {@code /}를 반복해서 제거한다. 대소문자는 그대로 두고 {@code ..} 구간은 해석하지
   * 않는다.
   *
   * <ul>
   *   <li>경로 중간의 {@code ./}, {@code //}와 끝의 {@code /}는 그대로 둔다
   *   <li>공백은 지우지 않는다. 공백으로 시작하는 경로는 앞의 {@code ./}도 제거되지 않는다
   *   <li>{@code ""}, {@code "/"}, {@code "./"}처럼 제거할 것만 있는 경로는 {@code ""}가 된다
   * </ul>
   */
  public static String normalize(String path) {
    String slashed = Objects.requireNonNull(path, "path").replace('\\', '/');
    int start = 0;
    while (true) {
      if (slashed.startsWith("./", start)) {
        start += 2;
      } else if (slashed.startsWith("/", start)) {
        start += 1;
      } else {
        return slashed.substring(start);
      }
    }
  }
}

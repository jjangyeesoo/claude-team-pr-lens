package com.prlens.glob;

/** 저장소 경로용 glob. 본문은 작업 6.1에서 구현한다. */
public final class GlobMatcher {

  private GlobMatcher() {}

  public static boolean matches(String pattern, String path) {
    throw new UnsupportedOperationException("작업 6.1에서 구현한다");
  }

  /**
   * @throws GlobSyntaxException 패턴 문법이 유효하지 않을 때
   */
  public static void validate(String pattern) throws GlobSyntaxException {
    throw new UnsupportedOperationException("작업 6.1에서 구현한다");
  }
}

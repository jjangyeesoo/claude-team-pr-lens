package com.prlens.cli;

/** {@code review} 명령이 인자를 검증한 뒤 실행을 넘기는 곳. 종료 코드를 돌려준다. */
@FunctionalInterface
public interface ReviewRunner {

  int run(ReviewArgs args);
}

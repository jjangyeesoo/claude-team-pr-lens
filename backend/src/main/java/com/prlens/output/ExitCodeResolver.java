package com.prlens.output;

import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewResult;
import com.prlens.model.Severity;

/**
 * 결과를 만들었을 때의 종료 코드를 정한다 (요구사항 15.9, 15.10, 16.7, 16.8). 결과를 만들지 못한 경우(종료 코드 2)는 {@code cli}가 정한다.
 */
public final class ExitCodeResolver {

  private ExitCodeResolver() {}

  public static int resolve(ReviewResult result) {
    if (result.findings().stream().anyMatch(finding -> finding.severity() == Severity.BLOCKER)) {
      return 1;
    }
    return result.status() == ResultStatus.INCOMPLETE ? 3 : 0;
  }
}

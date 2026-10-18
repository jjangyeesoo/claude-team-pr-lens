package com.prlens.cli;

import com.prlens.support.PrLensException;
import java.util.List;

/** 필요한 환경변수가 없거나 비어 있을 때 던진다. 없는 이름을 모두 담는다 (요구사항 1.10, 19.2, 19.3). */
public final class MissingSecretException extends PrLensException {

  public MissingSecretException(List<String> names) {
    super(render(names));
  }

  private static String render(List<String> names) {
    StringBuilder out = new StringBuilder("환경변수가 설정되지 않았거나 비어 있습니다: ");
    out.append(String.join(", ", names)).append('\n');
    out.append("터미널에서 값을 설정한 뒤 다시 실행하세요. 설정 파일이나 명령행 인자로는 줄 수 없습니다.\n");
    out.append("  macOS/Linux: export NAME=값\n");
    out.append("  PowerShell:  $env:NAME = \"값\"\n");
    out.append("  cmd:         set NAME=값");
    return out.toString();
  }
}

package com.prlens.config;

import com.prlens.support.PrLensException;
import java.util.List;

/**
 * 설정을 읽을 수 없을 때 던진다. 문제가 있는 항목을 모두 모아 한 번에 보고한다 (요구사항 18.7).
 *
 * <p>비밀 항목은 값을 읽지 않으므로 메시지에도 값이 없다. 문자열 값은 메시지에 싣지 않는다(잘못된 제외 패턴은 예외로 원문을 보여 준다).
 */
public final class ConfigException extends PrLensException {

  /**
   * @param item 문제가 있는 항목 이름. 점으로 이은 경로(예: {@code llm.effort})이고, 항목이 아닌 문제는 {@code syntax}, {@code
   *     encoding}, {@code --config} 같은 이름을 쓴다
   */
  public record Problem(String item, String message) {}

  private final List<Problem> problems;

  public ConfigException(List<Problem> problems) {
    super(render(problems));
    this.problems = List.copyOf(problems);
  }

  public static ConfigException of(String item, String message) {
    return new ConfigException(List.of(new Problem(item, message)));
  }

  public List<Problem> problems() {
    return problems;
  }

  private static String render(List<Problem> problems) {
    StringBuilder out = new StringBuilder("설정 오류 ").append(problems.size()).append("건:");
    for (Problem problem : problems) {
      out.append("\n- ").append(problem.item()).append(": ").append(problem.message());
    }
    return out.toString();
  }
}

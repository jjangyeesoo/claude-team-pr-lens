package com.prlens.config;

import com.prlens.model.Configuration;
import com.prlens.model.ModelPricing;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Configuration을 설정 파일 형식으로 출력한다 (요구사항 18.10). 모든 항목을 고정 순서로 빠짐없이 쓰고, 문자열은 항상 큰따옴표로 감싼다. {@code
 * **}로 시작하는 값이 YAML alias로, {@code true}나 {@code 123}이 다른 타입으로 읽히는 것을 막아 round-trip(요구사항 18.11)을
 * 지킨다.
 */
public final class ConfigPrinter {

  private ConfigPrinter() {}

  public static String print(Configuration configuration) {
    StringBuilder out = new StringBuilder();
    out.append("exclude:\n");
    out.append("  add: ").append(list(configuration.additionalExcludes())).append('\n');
    out.append("  remove: ").append(list(configuration.removedExcludes())).append('\n');
    out.append("review:\n");
    out.append("  sizeLimit: ").append(configuration.sizeLimit()).append('\n');
    out.append("llm:\n");
    out.append("  model: ").append(quote(configuration.model())).append('\n');
    out.append("  effort: ").append(quote(configuration.effort())).append('\n');
    out.append("  maxOutputTokens: ").append(configuration.maxOutputTokens()).append('\n');
    out.append("retry:\n");
    out.append("  maxRetries: ").append(configuration.maxRetries()).append('\n');
    out.append("cost:\n");
    out.append("  maxUsdPerReview: ")
        .append(cost(configuration.maxCostUsdPerReview()))
        .append('\n');
    if (configuration.pricing().isEmpty()) {
      out.append("  pricing: {}\n");
    } else {
      out.append("  pricing:\n");
      for (Map.Entry<String, ModelPricing> entry : configuration.pricing().entrySet()) {
        ModelPricing price = entry.getValue();
        out.append("    ")
            .append(quote(entry.getKey()))
            .append(": { input: ")
            .append(plain(price.inputPerMTok()))
            .append(", output: ")
            .append(plain(price.outputPerMTok()))
            .append(", cacheWrite: ")
            .append(plain(price.cacheWritePerMTok()))
            .append(", cacheRead: ")
            .append(plain(price.cacheReadPerMTok()))
            .append(" }\n");
      }
    }
    return out.toString();
  }

  private static String list(List<String> values) {
    return "[" + String.join(", ", values.stream().map(ConfigPrinter::quote).toList()) + "]";
  }

  /** 비용 상한은 scale 4로 저장되므로 그대로 쓰면 {@code 0.5000}이 된다. 소수 둘째 자리까지는 유지한다. */
  private static String cost(BigDecimal value) {
    BigDecimal trimmed = value.stripTrailingZeros();
    return (trimmed.scale() < 2 ? trimmed.setScale(2) : trimmed).toPlainString();
  }

  /** 단가는 끝의 0이 제거돼 {@code 20.00}이 {@code 2E+1}이 되므로 {@code toPlainString()}으로 쓴다. */
  private static String plain(BigDecimal value) {
    return value.stripTrailingZeros().toPlainString();
  }

  private static String quote(String value) {
    StringBuilder out = new StringBuilder("\"");
    value
        .codePoints()
        .forEach(
            codePoint -> {
              switch (codePoint) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                  boolean control =
                      codePoint < 0x20
                          || (codePoint >= 0x7F && codePoint <= 0x9F)
                          || codePoint == 0x2028
                          || codePoint == 0x2029
                          || codePoint == 0xFEFF;
                  if (control) {
                    out.append("\\u").append(String.format("%04X", codePoint));
                  } else {
                    out.appendCodePoint(codePoint);
                  }
                }
              }
            });
    return out.append('"').toString();
  }
}

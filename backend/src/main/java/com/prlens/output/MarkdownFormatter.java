package com.prlens.output;

import com.prlens.model.ChunkIssue;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.ExcludedFile;
import com.prlens.model.FileCountGap;
import com.prlens.model.Finding;
import com.prlens.model.LineVerdict;
import com.prlens.model.ResultStatus;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewMode;
import com.prlens.model.ReviewResult;
import com.prlens.model.ReviewStats;
import com.prlens.model.Severity;
import com.prlens.model.Usage;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * ReviewResult를 Markdown으로 만든다 (요구사항 15). 구역 순서와 형식은 design.md "T3 CLI 출력"을 따른다.
 *
 * <p>줄바꿈은 {@code \n}뿐이다. 결과와 컨텍스트에 든 문자열의 {@code \r\n}과 {@code \r}도 {@code \n}으로 바꾼다. 출력은 줄바꿈 한 번으로
 * 끝난다.
 */
public final class MarkdownFormatter {

  private static final Comparator<Finding> BY_FILE_THEN_LINE =
      Comparator.comparing(Finding::file, MarkdownFormatter::compareCodePoints)
          .thenComparing(Finding::line, Comparator.nullsFirst(Comparator.naturalOrder()));

  public String format(ReviewResult result, ReviewContext context) {
    StringBuilder out = new StringBuilder();
    if (result.status() == ResultStatus.INCOMPLETE) {
      appendWarning(out, result);
    }
    appendSummary(out, result);
    appendSeverityCounts(out, result.findings());
    appendInlineFindings(out, result.findings());
    appendSummaryOnlyFindings(out, result.findings());
    appendExcluded(out, result.excludedFileDetails());
    appendContext(out, context.files());
    appendUsage(out, result.usage());
    return out.toString().stripTrailing() + "\n";
  }

  private static void appendWarning(StringBuilder out, ReviewResult result) {
    out.append("## 불완전한 결과\n\n");
    out.append("이 결과는 불완전하므로 아래 지적 목록은 부분 결과입니다.\n\n");
    out.append("- 사유: ")
        .append(
            String.join(
                ", ", result.incompleteReasons().stream().map(MarkdownFormatter::name).toList()))
        .append('\n');
    List<ChunkIssue> chunks = result.incompleteDetails().chunks();
    if (!chunks.isEmpty()) {
      out.append("- 불완전한 Chunk 파일:\n");
      for (ChunkIssue chunk : chunks) {
        String files = chunk.files().isEmpty() ? "(파일 없음)" : String.join(", ", chunk.files());
        out.append("  - Chunk ")
            .append(chunk.chunkIndex())
            .append(" (")
            .append(name(chunk.reason()))
            .append("): ")
            .append(text(files))
            .append('\n');
      }
    }
    FileCountGap gap = result.incompleteDetails().fileCountGap();
    if (gap != null) {
      out.append("- 파일 수 부족: GitHub가 알린 ")
          .append(gap.reported())
          .append("개 중 ")
          .append(gap.received())
          .append("개만 받았습니다\n");
    }
    out.append('\n');
  }

  private static void appendSummary(StringBuilder out, ReviewResult result) {
    out.append("## 요약\n\n");
    if (!result.summary().isEmpty()) {
      out.append(text(result.summary())).append("\n\n");
    }
    ReviewStats stats = result.stats();
    if (stats.mode() == ReviewMode.SUMMARY_ONLY) {
      out.append("PR을 나누세요: 변경 줄 수 ")
          .append(stats.changedLineCount())
          .append("이 기준값 ")
          .append(3L * stats.sizeLimit())
          .append("(3×Size_Limit ")
          .append(stats.sizeLimit())
          .append(")을 넘어 요약만 생성했습니다.\n\n");
    }
  }

  private static void appendSeverityCounts(StringBuilder out, List<Finding> findings) {
    out.append("## 심각도별 지적 수\n\n");
    for (Severity severity : Severity.values()) {
      long count = findings.stream().filter(finding -> finding.severity() == severity).count();
      out.append("- ").append(name(severity)).append(": ").append(count).append('\n');
    }
    long demoted = findings.stream().filter(finding -> finding.demotion() != null).count();
    out.append("\n강등된 지적: ").append(demoted).append("건\n\n");
  }

  private static void appendInlineFindings(StringBuilder out, List<Finding> findings) {
    List<Finding> inline =
        findings.stream()
            .filter(finding -> finding.verdict() == LineVerdict.INLINE_ELIGIBLE)
            .sorted(BY_FILE_THEN_LINE)
            .toList();
    out.append("## 파일별 지적 (").append(inline.size()).append("건)\n\n");
    String currentFile = null;
    for (Finding finding : inline) {
      if (!finding.file().equals(currentFile)) {
        currentFile = finding.file();
        out.append("### ").append(text(currentFile)).append("\n\n");
      }
      String line = finding.line() == null ? "라인 없음" : "L" + finding.line();
      out.append("- `").append(line).append("` ");
      appendFindingBody(out, finding);
    }
    if (!inline.isEmpty()) {
      out.append('\n');
    }
  }

  private static void appendSummaryOnlyFindings(StringBuilder out, List<Finding> findings) {
    List<Finding> outside =
        findings.stream()
            .filter(finding -> finding.verdict() == LineVerdict.SUMMARY_ONLY)
            .sorted(BY_FILE_THEN_LINE)
            .toList();
    out.append("## 라인 밖 지적 (").append(outside.size()).append("건)\n\n");
    for (Finding finding : outside) {
      String where =
          finding.line() == null ? finding.file() : finding.file() + ":" + finding.line();
      out.append("- `")
          .append(text(where))
          .append("` (사유: ")
          .append(name(finding.summaryOnlyReason()))
          .append(") ");
      appendFindingBody(out, finding);
    }
    if (!outside.isEmpty()) {
      out.append('\n');
    }
  }

  private static void appendFindingBody(StringBuilder out, Finding finding) {
    out.append("**")
        .append(name(finding.severity()))
        .append("** · ")
        .append(name(finding.category()))
        .append("\n  ")
        .append(indented(finding.message()))
        .append('\n');
    if (finding.suggestion() != null) {
      out.append("  제안: ").append(indented(finding.suggestion())).append('\n');
    }
    out.append("  근거: ").append(name(finding.basis().type()));
    if (finding.basis().ref() != null) {
      out.append(" `").append(text(finding.basis().ref())).append('`');
    }
    out.append('\n');
  }

  private static void appendExcluded(StringBuilder out, List<ExcludedFile> excluded) {
    out.append("## 제외 파일 (").append(excluded.size()).append("건)\n\n");
    for (ExcludedFile file : excluded) {
      out.append("- `")
          .append(text(file.path()))
          .append("` — ")
          .append(text(file.reason()))
          .append('\n');
    }
    if (!excluded.isEmpty()) {
      out.append('\n');
    }
  }

  private static void appendContext(StringBuilder out, List<ContextFile> files) {
    out.append("## 사용된 컨텍스트 파일 (").append(files.size()).append(")\n\n");
    if (files.isEmpty()) {
      out.append("컨텍스트 파일이 없습니다.\n\n");
      return;
    }
    for (ContextSource source : ContextSource.values()) {
      List<ContextFile> group =
          files.stream()
              .filter(file -> file.source() == source)
              .sorted(Comparator.comparing(ContextFile::path, MarkdownFormatter::compareCodePoints))
              .toList();
      if (group.isEmpty()) {
        continue;
      }
      out.append("### ").append(name(source)).append("\n\n");
      group.forEach(file -> out.append("- `").append(text(file.path())).append("`\n"));
      out.append('\n');
    }
  }

  private static void appendUsage(StringBuilder out, Usage usage) {
    out.append("## 토큰 사용량과 추정 비용\n\n");
    out.append("- 모델: ").append(text(usage.model())).append('\n');
    out.append("- 입력 토큰: ").append(usage.inputTokens()).append('\n');
    out.append("- 출력 토큰: ").append(usage.outputTokens()).append('\n');
    out.append("- 캐시 쓰기 토큰: ").append(usage.cacheWriteTokens()).append('\n');
    out.append("- 캐시 읽기 토큰: ").append(usage.cacheReadTokens()).append('\n');
    out.append("- 추정 비용: ")
        .append(
            usage.estimatedCostUsd() == null
                ? "알 수 없음"
                : "$" + usage.estimatedCostUsd().toPlainString())
        .append('\n');
  }

  private static String name(Enum<?> value) {
    return value.name().toLowerCase(Locale.ROOT);
  }

  private static String text(String value) {
    return value.replace("\r\n", "\n").replace('\r', '\n');
  }

  /** 목록 항목 안에서 줄이 바뀌어도 항목 밖으로 나가지 않도록 이어지는 줄을 들여쓴다. */
  private static String indented(String value) {
    return text(value).replace("\n", "\n  ");
  }

  private static int compareCodePoints(String a, String b) {
    int i = 0;
    int j = 0;
    while (i < a.length() && j < b.length()) {
      int ca = a.codePointAt(i);
      int cb = b.codePointAt(j);
      if (ca != cb) {
        return Integer.compare(ca, cb);
      }
      i += Character.charCount(ca);
      j += Character.charCount(cb);
    }
    return Integer.compare(a.length() - i, b.length() - j);
  }
}

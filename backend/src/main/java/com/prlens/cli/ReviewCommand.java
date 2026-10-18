package com.prlens.cli;

import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.ITypeConverter;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.TypeConversionException;

/**
 * {@code prlens review <PR URL> [--format markdown|json] [--config <경로>]}. 인자를 검증해 {@link
 * ReviewRunner}에 넘긴다.
 */
@Command(name = "review", description = "PR을 팀 컨텍스트 기준으로 리뷰합니다.")
final class ReviewCommand implements Callable<Integer> {

  static final String ALLOWED_FORMATS = "markdown, json";

  private final ReviewRunner runner;

  @Parameters(index = "0", paramLabel = "<PR URL>", description = "리뷰할 GitHub PR 주소")
  private String url;

  @Option(
      names = "--format",
      paramLabel = "<형식>",
      converter = FormatConverter.class,
      defaultValue = "markdown",
      description = "출력 형식 (허용 값: " + ALLOWED_FORMATS + ", 기본값: markdown)")
  private OutputFormat format;

  @Option(names = "--config", paramLabel = "<경로>", description = "설정 파일 경로")
  private Path config;

  @Option(
      names = {"-h", "--help"},
      usageHelp = true,
      description = "도움말을 출력합니다.")
  private boolean help;

  ReviewCommand(ReviewRunner runner) {
    this.runner = runner;
  }

  @Override
  public Integer call() {
    return runner.run(new ReviewArgs(url, format, config));
  }

  /** 허용 값 밖이면 값과 허용 값을 담아 던진다 (요구사항 15.12). 대소문자는 구분한다. */
  static final class FormatConverter implements ITypeConverter<OutputFormat> {

    @Override
    public OutputFormat convert(String value) {
      return switch (value) {
        case "markdown" -> OutputFormat.MARKDOWN;
        case "json" -> OutputFormat.JSON;
        default ->
            throw new TypeConversionException(
                "'" + value + "'은(는) 허용되지 않는 값입니다 (허용 값: " + ALLOWED_FORMATS + ")");
      };
    }
  }
}

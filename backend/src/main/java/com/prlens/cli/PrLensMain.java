package com.prlens.cli;

import com.prlens.support.MaskingPrintStream;
import com.prlens.support.SecretMasker;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.ExecutionException;
import picocli.CommandLine.Help;
import picocli.CommandLine.MissingParameterException;
import picocli.CommandLine.Model.ArgSpec;
import picocli.CommandLine.Model.OptionSpec;
import picocli.CommandLine.ParameterException;
import picocli.CommandLine.ParseResult;

/** CLI 진입점 (ADR 0004). Spring 컨텍스트 없이 picocli로 명령을 해석한다. */
@Command(name = "prlens", description = "팀 컨텍스트 기준으로 GitHub PR을 리뷰합니다.")
public final class PrLensMain {

  private static final int USAGE_ERROR = 2;
  private static final String DEBUG_ENV = "PRLENS_DEBUG";

  private PrLensMain() {}

  /**
   * 환경변수 값을 읽자마자 마스커와 UTF-8 스트림을 가장 먼저 설치해, 인자·설정 오류 메시지의 비밀값도 가린다 (요구사항 19.4, 22.3). 스트림을 닫는 것은
   * 여기서 {@code System.exit} 직전뿐이다.
   */
  public static void main(String[] args) {
    Map<String, String> env = System.getenv();
    SecretMasker masker = CliPipeline.secretMasker(env);
    MaskingPrintStream out = MaskingPrintStream.stdout(masker);
    MaskingPrintStream err = MaskingPrintStream.stderr(masker);
    System.setOut(out);
    System.setErr(err);
    int code = USAGE_ERROR;
    try {
      Path workingDir = Path.of("").toAbsolutePath();
      Path homeDir = Path.of(System.getProperty("user.home"));
      code =
          run(
              args,
              out,
              err,
              CliPipeline.runner(
                  env,
                  workingDir,
                  homeDir,
                  preflight -> {
                    // 파이프라인 조립은 작업 18.3에서 구현한다
                    err.print("아직 구현되지 않았습니다.\n");
                    return USAGE_ERROR;
                  }),
              debug(env));
    } catch (Throwable e) {
      // run 밖(경로 계산 등)에서 난 오류도 스트림을 닫기 전에 알린다
      code = ErrorMapper.toExitCode(e, out, err, debug(env));
    } finally {
      out.flush();
      err.flush();
      out.close();
      err.close();
    }
    System.exit(code);
  }

  /** 환경변수 {@code PRLENS_DEBUG}가 비어 있지 않으면 디버그 모드다 (스펙에 켜는 방법이 없어 정한 제안값). */
  private static boolean debug(Map<String, String> env) {
    return !env.getOrDefault(DEBUG_ENV, "").isBlank();
  }

  /** 명령을 실행하고 종료 코드를 돌려준다. 어떤 오류든 stderr에 쓰고 2다. */
  static int run(String[] args, PrintStream out, PrintStream err, ReviewRunner runner) {
    return run(args, out, err, runner, false);
  }

  /**
   * {@code main}이 스트림을 닫기 전에 오류 메시지를 쓰도록 {@code run}을 빠져나가는 예외가 없게 한다. 빠져나가면 닫힌 stderr에 쓰여 메시지 없이
   * 종료 코드 1로 끝난다.
   */
  static int run(
      String[] args, PrintStream out, PrintStream err, ReviewRunner runner, boolean debug) {
    try {
      return execute(args, out, err, runner);
    } catch (Throwable e) {
      return ErrorMapper.toExitCode(e, out, err, debug);
    } finally {
      out.flush();
      err.flush();
    }
  }

  /**
   * 인자 오류는 {@link UsageException}으로, 실행 중 예외는 그대로 던진다. 종료 코드는 호출자가 한 번에 정한다. picocli의 {@code
   * execute()}는 오류 처리기가 던진 예외를 삼키고 스택을 출력하므로(확인함) 쓰지 않고, 파싱과 실행을 나눠 부른다.
   */
  static int execute(String[] args, PrintStream out, PrintStream err, ReviewRunner runner) {
    CommandLine cmd = commandLine(out, err, runner);
    ParseResult parsed;
    try {
      parsed = cmd.parseArgs(args);
    } catch (ParameterException ex) {
      throw new UsageException(usageMessage(ex));
    }
    if (!parsed.hasSubcommand() && !parsed.isUsageHelpRequested()) {
      throw new UsageException(
          "명령을 지정하세요.\n사용법: prlens review <PR URL> [--format markdown|json] [--config <경로>]");
    }
    try {
      return cmd.getExecutionStrategy().execute(parsed);
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof RuntimeException cause) {
        throw cause;
      }
      throw ex;
    } finally {
      cmd.getOut().flush();
    }
  }

  /** 사용법 출력은 OS 코드 페이지와 무관하게 UTF-8로 쓴다. */
  static CommandLine commandLine(PrintStream out, PrintStream err, ReviewRunner runner) {
    CommandLine cmd = new CommandLine(new PrLensMain());
    cmd.addSubcommand("review", new ReviewCommand(runner));
    cmd.setOut(new PrintWriter(out, true, StandardCharsets.UTF_8));
    cmd.setErr(new PrintWriter(err, true, StandardCharsets.UTF_8));
    cmd.setColorScheme(Help.defaultColorScheme(Help.Ansi.OFF));
    return cmd;
  }

  private static String usageMessage(ParameterException ex) {
    String message = ex.getMessage();
    // 값이 없을 때의 picocli 메시지에는 허용 값이 없다. 값이 틀린 경우는 변환기가 이미 담았다
    boolean formatValueMissing =
        !message.contains(ReviewCommand.ALLOWED_FORMATS)
            && (isFormatOption(ex.getArgSpec())
                || ex instanceof MissingParameterException missing
                    && missing.getMissing().stream().anyMatch(PrLensMain::isFormatOption));
    return formatValueMissing
        ? message + " (허용 값: " + ReviewCommand.ALLOWED_FORMATS + ")"
        : message;
  }

  private static boolean isFormatOption(ArgSpec spec) {
    return spec instanceof OptionSpec option && "--format".equals(option.longestName());
  }
}

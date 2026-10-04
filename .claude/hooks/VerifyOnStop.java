// Stop hook: Claude가 응답을 마칠 때, 코드가 바뀐 스택(backend, web)만 골라 포맷 + 검증을 실행한다.
//
// 스택마다 따로 판단한다 (STACKS 목록)
// - 그 스택 폴더의 "현재 내용"(커밋 + 스테이징 + 미스테이징 + untracked, .gitignore 제외)을 git 트리 해시로 계산하고,
//   마지막으로 검증을 통과한 내용과 다르면 실행한다. 해시는 실제 인덱스를 복사한 임시 인덱스로 계산하므로
//   사용자의 스테이징 상태는 바뀌지 않는다.
//   → 커밋, pull, 브랜치 전환으로 내용이 바뀌어도 잡고, 통과한 내용을 그대로 커밋하거나 git add만 한 경우는 건너뛴다.
// - 처음 실행이고 작업 트리가 깨끗하면 커밋된 상태를 기준으로 삼고 건너뛴다.
// - 문서만 바뀌었으면(IGNORED) 건너뛴다. git 저장소가 아니면 아무것도 하지 않는다.
// 여러 스택이 바뀌었으면 병렬로 실행한다 (전체 시간이 settings.json의 hook timeout을 넘지 않도록).
//
// 실패하면 종료 코드 2로 멈춤을 막고 실패 로그를 Claude에게 전달한다.
// 이미 한 번 막았는데(stop_hook_active) 또 실패하면 더 막지 않고 사용자에게 알린다 (무한 루프 방지).
// 도구가 실행조차 못 한 경우(의존성 미설치, 명령 없음, 시간 초과)는 코드 문제가 아니므로 막지 않고 알리기만 한다.
// 이때 통과 기록을 남기지 않으므로, 도구가 준비되면 다음 Stop에서 다시 검증한다.
// 주의: 사용자가 직접 작성 중인 미완성 코드도 검증 대상이다. 테스트가 깨진 상태로 두고 질문만 해도 한 번 막힌다.
//
// JDK 17+와 git만 있으면 OS와 무관하게 동작한다 (web 검증에는 Node/npm이 추가로 필요).

import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

public class VerifyOnStop {

  /**
   * 검증 대상 스택. 스택을 추가·변경하면 이 목록만 고친다.
   *
   * @param dir 저장소 루트 기준 폴더. 이 폴더의 변경만 이 스택의 검증을 일으키고, 명령도 이 폴더에서 실행한다
   * @param tool PATH에서 찾아야 하는 실행 파일 (없으면 알리기만 함). 폴더 안의 스크립트만 쓰면 null
   * @param prerequisite 이 파일이 없으면 실행하지 않고 알리기만 한다 (예: npm ci 전)
   * @param failureMarkers 출력에 이 문자열이 있어야 "코드 실패"로 본다. 비어 있으면 종료 코드만으로 판단
   * @param nativeOutput 출력이 OS 기본 인코딩(한국어 Windows는 MS949)인가. Gradle은 true, Node 도구는 UTF-8
   */
  record Stack(
      String name,
      String dir,
      List<String> windowsCommand,
      List<String> unixCommand,
      String tool,
      String prerequisite,
      String setupHint,
      List<String> failureMarkers,
      boolean nativeOutput,
      String reportHint) {}

  // Windows: 명시적 상대 경로(.\gradlew.bat)로 실행한다. 절대 경로를 넘기면 &, ^ 같은 문자를 cmd가 잘못 해석하고,
  // NoDefaultCurrentDirectoryInExePath가 설정된 환경에서는 "gradlew.bat"만으로는 현재 폴더를 찾지 않는다.
  // macOS/Linux: sh로 실행해 gradlew 실행 권한이 없어도 동작하게 한다.
  private static final List<Stack> STACKS =
      List.of(
          new Stack(
              "backend",
              "backend",
              List.of(
                  "cmd", "/c", ".\\gradlew.bat", "spotlessApply", "test", "--console=plain",
                  "--warning-mode=none"),
              List.of(
                  "sh", "./gradlew", "spotlessApply", "test", "--console=plain",
                  "--warning-mode=none"),
              null,
              "gradlew",
              "JDK 17 이상이 설치되어 있는지 확인하세요",
              List.of("FAILURE:", "BUILD FAILED"),
              true,
              "backend/build/reports/tests/test/index.html"),
          new Stack(
              "web",
              "web",
              List.of("cmd", "/c", "npm", "run", "verify"),
              List.of("npm", "run", "verify"),
              "npm",
              "node_modules",
              "web 폴더에서 `npm ci`를 먼저 실행하세요",
              List.of(),
              false,
              "web에서 `npm run verify`로 재현"));

  // 검증을 건너뛸 경로 (git pathspec). 스택 폴더 안에서도 문서 변경은 검증하지 않는다.
  private static final List<String> IGNORED =
      List.of(":(exclude)docs", ":(exclude).claude", ":(exclude).github", ":(glob,exclude)**/*.md");
  private static final long TIMEOUT_SECONDS = 540; // settings.json의 hook timeout(600초)보다 짧게
  private static final int TAIL_LINES = 60;
  private static final int TIMEOUT_EXIT = 124;

  private static final PrintStream OUT = new PrintStream(System.out, true, StandardCharsets.UTF_8);
  private static final PrintStream ERR = new PrintStream(System.err, true, StandardCharsets.UTF_8);
  private static final boolean WINDOWS =
      System.getProperty("os.name").toLowerCase().contains("win");

  public static void main(String[] args) throws Exception {
    String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    boolean alreadyBlocked = input.matches("(?s).*\"stop_hook_active\"\\s*:\\s*true.*");

    Path root = projectRoot();
    if (git(root, Map.of(), "rev-parse", "--git-dir") == null) return; // git 저장소가 아님
    Path stateDir = root.resolve("build/claude-verify");
    Path stateFile = stateDir.resolve("state.properties");
    Properties state = loadState(stateFile);
    Files.createDirectories(stateDir);

    // 1) 스택별로 실행이 필요한지 판단하고, 필요한 것은 병렬로 시작한다
    List<Run> runs = new ArrayList<>();
    List<String> notices = new ArrayList<>();
    for (Stack stack : STACKS) {
      Path dir = root.resolve(stack.dir());
      if (!Files.isDirectory(dir)) continue; // 이 저장소에 없는 스택
      String content = contentHash(root, stateDir, stack);
      if (content == null) continue; // git이 계산하지 못함: 판단 근거가 없으므로 건너뜀
      String key = "lastPass." + stack.name();
      String lastPass = state.getProperty(key);
      if (lastPass == null && !hasWorkingChanges(root, stack)) {
        state.setProperty(key, content); // 첫 실행 + 깨끗한 트리: 커밋된 상태를 기준으로 삼는다
        continue;
      }
      if (content.equals(lastPass)) continue;

      String unavailable = unavailableReason(stack, dir);
      if (unavailable != null) {
        notices.add(stack.name() + ": 검증을 건너뛰었습니다. " + unavailable);
        continue;
      }
      Path log = stateDir.resolve(stack.name() + ".log");
      try {
        runs.add(Run.start(stack, dir, log));
      } catch (IOException e) {
        notices.add(stack.name() + ": 검증 명령을 시작하지 못했습니다 (" + e.getMessage() + ")");
      }
    }

    // 2) 결과 수집. 통과한 스택은 실행 후 내용(포맷터가 파일을 바꿨을 수 있음)을 통과 기록으로 남긴다
    long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(TIMEOUT_SECONDS);
    List<String> failures = new ArrayList<>();
    for (Run run : runs) {
      int exitCode = run.await(deadline);
      String output =
          readLog(run.log, run.stack.nativeOutput() ? nativeCharset() : StandardCharsets.UTF_8);
      if (exitCode == 0) {
        String after = contentHash(root, stateDir, run.stack);
        if (after != null) state.setProperty("lastPass." + run.stack.name(), after);
      } else if (isCodeFailure(run.stack, exitCode, output)) {
        failures.add(failureReport(run, output));
      } else {
        notices.add(
            run.stack.name()
                + ": 검증 도구를 실행하지 못했습니다 (exit "
                + exitCode
                + "). 로그: build/claude-verify/"
                + run.stack.name()
                + ".log\n"
                + tail(output, 5));
      }
    }
    saveState(stateFile, state); // 실패하거나 실행하지 못한 스택은 기록하지 않으므로 다음에 다시 검증한다

    if (failures.isEmpty()) {
      if (!notices.isEmpty()) systemMessage("VerifyOnStop: " + String.join("\n", notices));
      return;
    }
    if (alreadyBlocked) {
      List<String> msg = new ArrayList<>();
      msg.add("VerifyOnStop: 검증이 여전히 실패합니다. 직접 확인이 필요합니다. 로그: build/claude-verify/");
      msg.addAll(notices);
      systemMessage(String.join("\n", msg));
      return;
    }
    List<String> sections = new ArrayList<>(failures);
    sections.addAll(notices);
    ERR.println(
        "VerifyOnStop: 검증 실패. 근본 원인을 고치고 다시 검증하세요 (테스트를 지우거나 skip하지 말 것).\n\n"
            + String.join("\n\n", sections));
    System.exit(2);
  }

  /** 실행할 수 없는 이유. 실행할 수 있으면 null. */
  static String unavailableReason(Stack stack, Path dir) throws Exception {
    if (stack.tool() != null && !onPath(stack.tool())) {
      return "`" + stack.tool() + "`을(를) PATH에서 찾지 못했습니다. Claude Code를 실행한 셸의 PATH를 확인하세요";
    }
    if (!Files.exists(dir.resolve(stack.prerequisite()))) return stack.setupHint();
    return null;
  }

  /** 명령이 PATH에 있는가. cmd /c로 없는 명령을 실행하면 종료 코드가 1이라 실패와 구분할 수 없으므로 미리 확인한다. */
  static boolean onPath(String tool) throws Exception {
    List<String> cmd = WINDOWS ? List.of("where", tool) : List.of("sh", "-c", "command -v " + tool);
    try {
      Process p =
          new ProcessBuilder(cmd)
              .redirectOutput(ProcessBuilder.Redirect.DISCARD)
              .redirectError(ProcessBuilder.Redirect.DISCARD)
              .start();
      return p.waitFor() == 0;
    } catch (IOException e) {
      return false;
    }
  }

  static boolean isCodeFailure(Stack stack, int exitCode, String output) {
    if (exitCode == TIMEOUT_EXIT) return false;
    if (stack.failureMarkers().isEmpty()) return true;
    return stack.failureMarkers().stream().anyMatch(output::contains);
  }

  static String failureReport(Run run, String output) {
    String command =
        String.join(" ", WINDOWS ? run.stack.windowsCommand() : run.stack.unixCommand());
    return "["
        + run.stack.name()
        + "] `"
        + command
        + "` 실패\n"
        + "전체 로그: build/claude-verify/"
        + run.stack.name()
        + ".log, 참고: "
        + run.stack.reportHint()
        + "\n"
        + "--- 마지막 "
        + TAIL_LINES
        + "줄 ---\n"
        + tail(output, TAIL_LINES);
  }

  /** 실행 중인 스택 검증 프로세스. */
  static final class Run {
    final Stack stack;
    final Path log;
    final Process process;

    private Run(Stack stack, Path log, Process process) {
      this.stack = stack;
      this.log = log;
      this.process = process;
    }

    static Run start(Stack stack, Path dir, Path log) throws IOException {
      List<String> cmd = WINDOWS ? stack.windowsCommand() : stack.unixCommand();
      Process p =
          new ProcessBuilder(cmd)
              .directory(dir.toFile())
              .redirectErrorStream(true)
              .redirectOutput(log.toFile())
              .start();
      p.getOutputStream().close();
      return new Run(stack, log, p);
    }

    /** 공통 마감 시각까지 기다린다. 넘기면 프로세스 트리를 종료하고 TIMEOUT_EXIT를 돌려준다. */
    int await(long deadlineNanos) throws Exception {
      long remaining = Math.max(0, deadlineNanos - System.nanoTime());
      if (process.waitFor(remaining, TimeUnit.NANOSECONDS)) return process.exitValue();
      process.descendants().forEach(ProcessHandle::destroyForcibly);
      process.destroyForcibly();
      process.waitFor(5, TimeUnit.SECONDS); // 로그 파일 핸들이 닫히길 잠깐 기다린다
      Files.writeString(
          log,
          "\n[VerifyOnStop] " + TIMEOUT_SECONDS + "초 안에 끝나지 않아 중단했습니다.\n",
          stack.nativeOutput() ? nativeCharset() : StandardCharsets.UTF_8,
          java.nio.file.StandardOpenOption.APPEND);
      return TIMEOUT_EXIT;
    }
  }

  static Path projectRoot() {
    String dir = System.getenv("CLAUDE_PROJECT_DIR");
    return Path.of(dir == null || dir.isBlank() ? "." : dir).toAbsolutePath().normalize();
  }

  /**
   * 스택 폴더의 현재 내용을 나타내는 git 트리 해시. 실제 인덱스를 복사한 임시 인덱스에 작업 트리를 모두 add한 뒤
   * write-tree로 계산한다 (.gitignore 대상 제외, 사용자의 인덱스는 건드리지 않음). 계산할 수 없으면 null.
   */
  static String contentHash(Path root, Path stateDir, Stack stack) throws Exception {
    Path tmpIndex = stateDir.resolve("index-" + stack.name());
    Map<String, String> env = Map.of("GIT_INDEX_FILE", tmpIndex.toString());
    try {
      String indexPath = git(root, Map.of(), "rev-parse", "--git-path", "index");
      Path realIndex = indexPath == null ? null : root.resolve(indexPath.trim());
      if (realIndex != null && Files.exists(realIndex)) {
        // 실제 인덱스의 stat 캐시를 재사용해야 add가 바뀐 파일만 해시한다 (빠름)
        Files.copy(realIndex, tmpIndex, StandardCopyOption.REPLACE_EXISTING);
      } else if (git(root, env, "read-tree", "--empty") == null) {
        return null;
      }
      List<String> add = new ArrayList<>(List.of("add", "-A", "--", stack.dir()));
      add.addAll(IGNORED);
      if (git(root, env, add.toArray(String[]::new)) == null) return null;
      String tree = git(root, env, "write-tree", "--prefix=" + stack.dir() + "/");
      return tree == null ? "empty" : tree.trim(); // 폴더에 추적 대상 파일이 하나도 없으면 실패한다
    } finally {
      Files.deleteIfExists(tmpIndex);
      Files.deleteIfExists(stateDir.resolve("index-" + stack.name() + ".lock"));
    }
  }

  /** 스택 폴더에 커밋되지 않은 변경(문서 제외)이 있는가. */
  static boolean hasWorkingChanges(Path root, Stack stack) throws Exception {
    List<String> args =
        new ArrayList<>(List.of("status", "--porcelain", "--untracked-files=all", "--", stack.dir()));
    args.addAll(IGNORED);
    String out = git(root, Map.of(), args.toArray(String[]::new));
    return out == null || !out.isBlank();
  }

  /** Gradle/cmd 출력은 OS 기본 인코딩(한국어 Windows는 MS949)으로 나온다. */
  static Charset nativeCharset() {
    String name = System.getProperty("native.encoding");
    try {
      return name == null ? Charset.defaultCharset() : Charset.forName(name);
    } catch (Exception e) {
      return Charset.defaultCharset();
    }
  }

  static String readLog(Path log, Charset charset) throws Exception {
    if (!Files.exists(log)) return "";
    return new String(Files.readAllBytes(log), charset).replace("\r\n", "\n");
  }

  static void systemMessage(String msg) {
    OUT.println("{\"systemMessage\": \"" + jsonEscape(msg) + "\"}");
  }

  /**
   * git을 실행하고 stdout을 돌려준다. 실패하면 null. stderr(줄바꿈 경고 등)는 버린다: stdout과 섞이면 파싱이 깨진다.
   * 경로는 UTF-8 원문으로 받는다 (core.quotePath=false).
   */
  static String git(Path root, Map<String, String> env, String... args) throws Exception {
    List<String> cmd = new ArrayList<>(List.of("git", "-c", "core.quotePath=false"));
    cmd.addAll(List.of(args));
    ProcessBuilder pb =
        new ProcessBuilder(cmd)
            .directory(root.toFile())
            .redirectError(ProcessBuilder.Redirect.DISCARD);
    pb.environment().putAll(env);
    Process p = pb.start();
    p.getOutputStream().close();
    String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    return p.waitFor() == 0 ? out : null;
  }

  static Properties loadState(Path file) {
    Properties props = new Properties();
    try (var in = Files.newInputStream(file)) {
      props.load(in);
    } catch (Exception ignored) {
      // 첫 실행이거나 build/가 지워진 경우
    }
    return props;
  }

  static void saveState(Path file, Properties props) {
    try {
      Files.createDirectories(file.getParent());
      try (var out = Files.newOutputStream(file)) {
        props.store(out, "VerifyOnStop state");
      }
    } catch (Exception ignored) {
      // 상태 저장 실패는 다음 실행에서 검증을 한 번 더 할 뿐이다
    }
  }

  static String tail(String text, int n) {
    String[] lines =
        Arrays.stream(text.split("\n"))
            .filter(l -> !l.startsWith("> Task ") && !l.contains("Sharing is only supported"))
            .toArray(String[]::new);
    int from = Math.max(0, lines.length - n);
    return String.join("\n", Arrays.copyOfRange(lines, from, lines.length));
  }

  static String jsonEscape(String s) {
    StringBuilder b = new StringBuilder();
    for (char c : s.toCharArray()) {
      switch (c) {
        case '"' -> b.append("\\\"");
        case '\\' -> b.append("\\\\");
        case '\n' -> b.append("\\n");
        case '\r' -> b.append("\\r");
        case '\t' -> b.append("\\t");
        default -> {
          if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
          else b.append(c);
        }
      }
    }
    return b.toString();
  }
}

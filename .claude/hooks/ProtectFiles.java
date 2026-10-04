// PreToolUse hook (Edit|Write|NotebookEdit): 보호 대상 파일 수정을 차단한다.
// JDK 17+만 있으면 OS와 무관하게 동작한다 (java ProtectFiles.java, 단일 파일 실행).
// 종료 코드 2 = 차단. stderr 메시지는 Claude에게 피드백으로 전달된다.
//
// 1차 방어선은 settings.json의 deny 규칙이고, 이 hook은 2차 방어선이다.
// 이 hook은 파일 편집 도구만 본다. 스크립트가 간접적으로 파일을 쓰는 경우는 막지 못한다 (README 참고).
// 입력을 해석하지 못하면 예외로 종료 코드 1이 되어 편집이 허용된다(fail-open). 도구가 멈추는 것보다 낫다고 판단했다.

import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ProtectFiles {

  private static final Pattern PATH_FIELD =
      Pattern.compile("\"(?:file_path|notebook_path)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");

  public static void main(String[] args) throws Exception {
    String input = new String(System.in.readAllBytes(), StandardCharsets.UTF_8);
    Matcher m = PATH_FIELD.matcher(toolInput(input));
    if (!m.find()) {
      return;
    }
    String original = unescape(m.group(1));
    String projectDir = System.getenv("CLAUDE_PROJECT_DIR");
    String reason = protectedReason(relativeToProject(normalize(original), projectDir));
    if (reason == null) {
      // 심볼릭 링크·junction을 거쳐 보호 파일을 가리키는 경로: 실제 경로로 한 번 더 검사한다
      String real = realPath(original);
      if (real != null) {
        reason = protectedReason(relativeToProject(normalize(real), realPath(projectDir)));
        if (reason != null) reason += ", 실제 경로: " + real;
      }
    }
    if (reason != null) {
      PrintStream err = new PrintStream(System.err, true, StandardCharsets.UTF_8);
      err.println(
          "Blocked by .claude/hooks/ProtectFiles.java: "
              + original
              + " ("
              + reason
              + "). 이 파일은 사람이 직접 수정해야 합니다. 필요하면 사용자에게 요청하세요.");
      System.exit(2);
    }
  }

  /** tool_input 객체 부분만 대상으로 한다 (content 안에 "file_path" 문자열이 있어도 오탐하지 않도록). */
  static String toolInput(String input) {
    int i = input.indexOf("\"tool_input\"");
    return i < 0 ? input : input.substring(i);
  }

  /**
   * 소문자, 슬래시 구분자, 맨 앞 '/'로 정규화한다. Windows·macOS 파일시스템은 대소문자를 구분하지 않는다.
   * Windows는 이름 끝의 점과 공백을 무시하므로(gradlew. → gradlew, .git./ → .git/) 이름마다 끝의 점·공백을 뗀다.
   * '.'과 '..'는 그대로 둔다.
   */
  static String normalize(String path) {
    String p = path.replace('\\', '/').toLowerCase(Locale.ROOT);
    p = p.replaceAll("(?<=[^/. ])[. ]+(?=/|$)", "");
    return p.startsWith("/") ? p : "/" + p;
  }

  /**
   * 프로젝트 폴더 기준 경로로 바꾼다 (맨 앞 '/' 유지). 프로젝트가 C:\secrets\... 처럼 보호 이름이 들어간 폴더 아래 있어도
   * 오탐하지 않게 하기 위해서다. 프로젝트 밖 경로는 그대로 둔다.
   */
  static String relativeToProject(String path, String dir) {
    if (dir == null || dir.isBlank()) return path;
    String root = normalize(dir);
    if (!root.endsWith("/")) root += "/";
    return path.startsWith(root) ? path.substring(root.length() - 1) : path;
  }

  /**
   * 링크를 모두 푼 실제 경로. 아직 없는 파일(새로 만드는 경우)은 존재하는 가장 가까운 상위 폴더까지만 풀고 나머지를 붙인다.
   * 풀 수 없으면 null (이때는 문자열 검사 결과만 쓴다).
   */
  static String realPath(String path) {
    if (path == null || path.isBlank()) return null;
    try {
      Path target = Path.of(path).toAbsolutePath();
      Path existing = target;
      while (existing != null && !Files.exists(existing)) existing = existing.getParent();
      if (existing == null) return null;
      return existing.toRealPath().resolve(existing.relativize(target)).toString();
    } catch (Exception e) {
      return null;
    }
  }

  static String protectedReason(String path) {
    // Windows 파일시스템 별칭: 같은 파일을 다른 이름으로 가리켜 아래 이름 검사를 우회할 수 있다
    if (path.indexOf(':', 3) >= 0) return "NTFS 대체 데이터 스트림 경로(파일명:스트림)";
    if (path.matches(".*/[^/]*~\\d[^/]*(/.*)?")) return "8.3 짧은 이름(예: ENV~1)으로 보이는 경로";
    String name = path.substring(path.lastIndexOf('/') + 1);
    if (name.equals(".env") || name.startsWith(".env.")) return "환경변수/비밀값 파일";
    if (name.startsWith("application-local.") || name.startsWith("application-secret."))
      return "로컬/비밀 Spring 설정";
    if (path.contains("/secrets/")) return "secrets 디렉터리";
    if (path.contains("/.git/")) return "git 내부 파일";
    if (path.contains("/gradle/wrapper/") || name.equals("gradlew") || name.equals("gradlew.bat"))
      return "Gradle wrapper (버전 변경은 ./gradlew wrapper로)";
    if (name.equals("package-lock.json")) return "npm lock 파일 (npm install/npm ci가 갱신)";
    if (path.contains("/node_modules/")) return "설치된 의존성 (npm이 관리)";
    if (path.contains("/.next/")) return "Next.js 빌드 산출물";
    return null;
  }

  static String unescape(String s) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      if (c == '\\' && i + 1 < s.length()) {
        char n = s.charAt(++i);
        switch (n) {
          case 'n' -> out.append('\n');
          case 't' -> out.append('\t');
          case 'u' -> {
            out.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
            i += 4;
          }
          default -> out.append(n); // \\ \" \/
        }
      } else {
        out.append(c);
      }
    }
    return out.toString();
  }
}

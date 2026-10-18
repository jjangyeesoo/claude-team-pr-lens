package com.prlens.cli;

import com.prlens.config.ConfigLoader;
import com.prlens.model.Configuration;
import com.prlens.pullrequest.PullRequestUrl;
import com.prlens.support.SecretMasker;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;

/** 실행 전 검증과 (작업 18.3부터) 파이프라인 조립. */
final class CliPipeline {

  static final String GITHUB_TOKEN = "GITHUB_TOKEN";
  static final String ANTHROPIC_API_KEY = "ANTHROPIC_API_KEY";

  static final String URL_EXAMPLE = "https://github.com/{owner}/{repo}/pull/{number}";

  /** 검증을 통과한 입력. 비밀값이 로그에 새지 않도록 {@code toString}에서 뺀다. */
  record Preflight(
      PullRequestUrl url,
      OutputFormat format,
      Configuration config,
      String githubToken,
      String anthropicApiKey) {

    @Override
    public String toString() {
      return "Preflight[url=" + url + ", format=" + format + "]";
    }
  }

  private CliPipeline() {}

  /**
   * 인자(URL) → 설정 → 환경변수 순서로 검사한다. 실패하면 외부 API를 부르기 전에 예외를 던진다 (요구사항 1.9, 1.10, 18.7, 19.2, 19.3).
   * {@code --format}은 명령이 이미 검증했다.
   */
  static Preflight preflight(
      ReviewArgs args, Map<String, String> env, Path workingDir, Path homeDir) {
    PullRequestUrl url =
        PullRequestUrl.parse(args.url())
            .orElseThrow(
                () ->
                    new UsageException(
                        "PR 주소 형식이 올바르지 않습니다: " + args.url() + "\n올바른 형식: " + URL_EXAMPLE));
    Configuration config = new ConfigLoader(workingDir, homeDir).load(args.config());
    String githubToken = stripped(env, GITHUB_TOKEN);
    String anthropicKey = stripped(env, ANTHROPIC_API_KEY);
    List<String> missing = new ArrayList<>();
    if (githubToken.isEmpty()) {
      missing.add(GITHUB_TOKEN);
    }
    if (anthropicKey.isEmpty()) {
      missing.add(ANTHROPIC_API_KEY);
    }
    if (!missing.isEmpty()) {
      throw new MissingSecretException(missing);
    }
    return new Preflight(url, args.format(), config, githubToken, anthropicKey);
  }

  /**
   * 환경변수 값을 받은 그대로와 다듬은 값 둘 다 등록한다. 요청에는 다듬은 값을 쓰므로, 끝의 공백이나 {@code \r}이 있어도 어느 쪽 형태로 메시지에 나오든 가려진다
   * (요구사항 19.4). 빈 값은 {@link SecretMasker}가 뺀다.
   */
  static SecretMasker secretMasker(Map<String, String> env) {
    List<String> values = new ArrayList<>();
    for (String name : List.of(GITHUB_TOKEN, ANTHROPIC_API_KEY)) {
      String raw = env.get(name);
      if (raw != null) {
        values.add(raw);
        values.add(raw.strip());
      }
    }
    return new SecretMasker(values);
  }

  /** 검증을 통과했을 때만 {@code next}를 부르는 실행기. 실패는 예외로 던진다. */
  static ReviewRunner runner(
      Map<String, String> env, Path workingDir, Path homeDir, ToIntFunction<Preflight> next) {
    return args -> next.applyAsInt(preflight(args, env, workingDir, homeDir));
  }

  private static String stripped(Map<String, String> env, String name) {
    String value = env.get(name);
    return value == null ? "" : value.strip();
  }
}

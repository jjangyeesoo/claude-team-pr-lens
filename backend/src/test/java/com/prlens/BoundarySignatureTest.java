package com.prlens;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.ContextCollector;
import com.prlens.diff.DiffPrinter;
import com.prlens.diff.LineRanges;
import com.prlens.filter.DiffFilter;
import com.prlens.filter.FilterOutcome;
import com.prlens.github.FileFetch;
import com.prlens.github.FilePage;
import com.prlens.github.GitHubClient;
import com.prlens.github.GitHubCredentials;
import com.prlens.github.HttpGitHubClient;
import com.prlens.github.PullRequestMeta;
import com.prlens.github.RepoTree;
import com.prlens.glob.GlobMatcher;
import com.prlens.llm.LlmClient;
import com.prlens.llm.RetryingLlmClient;
import com.prlens.model.Configuration;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.model.ReviewContext;
import com.prlens.model.ReviewResult;
import com.prlens.pullrequest.PrFetcher;
import com.prlens.review.ReviewEngine;
import com.prlens.review.ReviewListener;
import com.prlens.support.Attempt;
import com.prlens.support.RetryExecutor;
import com.prlens.support.RetryListener;
import com.prlens.support.RetryPolicy;
import com.prlens.support.Sleeper;
import com.prlens.support.Warning;
import com.prlens.support.WarningSink;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

/** 트랙이 병렬로 구현하는 동안 바뀌면 안 되는 시그니처 (design.md "공유 경계", 요구사항 17.2, 17.3). 본문 구현과 무관하게 유지되는 것만 확인한다. */
class BoundarySignatureTest {

  // 요구사항 17.2: PR_Fetcher는 PullRequestSnapshot을 출력한다
  @Test
  void prFetcherTakesRepoAndNumberAndReturnsSnapshot() throws Exception {
    assertThat(PrFetcher.class.getConstructor(GitHubClient.class, WarningSink.class)).isNotNull();
    assertThat(PrFetcher.class.getMethod("fetch", RepoRef.class, int.class).getReturnType())
        .isEqualTo(PullRequestSnapshot.class);
  }

  // 요구사항 17.2: Context_Collector는 PullRequestSnapshot을 받아 ReviewContext를 출력한다
  @Test
  void contextCollectorTakesSnapshotAndReturnsReviewContext() throws Exception {
    assertThat(ContextCollector.class.getConstructor(GitHubClient.class, WarningSink.class))
        .isNotNull();
    assertThat(
            ContextCollector.class
                .getMethod("collect", PullRequestSnapshot.class, Configuration.class)
                .getReturnType())
        .isEqualTo(ReviewContext.class);
  }

  // 요구사항 17.3: Review_Engine의 입력은 snapshot, context, configuration 셋뿐이다
  @Test
  void reviewEngineTakesOnlySnapshotContextAndConfiguration() throws Exception {
    assertThat(ReviewEngine.class.getConstructor(LlmClient.class, ReviewListener.class))
        .isNotNull();

    List<Method> reviewMethods =
        List.of(ReviewEngine.class.getDeclaredMethods()).stream()
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .toList();

    assertThat(reviewMethods).hasSize(1);
    assertThat(reviewMethods.get(0).getName()).isEqualTo("review");
    assertThat(reviewMethods.get(0).getParameterTypes())
        .containsExactly(PullRequestSnapshot.class, ReviewContext.class, Configuration.class);
    assertThat(reviewMethods.get(0).getReturnType()).isEqualTo(ReviewResult.class);
  }

  @Test
  void boundaryEntryPointsAreFinalClasses() {
    assertThat(List.of(PrFetcher.class, ContextCollector.class, ReviewEngine.class))
        .allMatch(type -> Modifier.isFinal(type.getModifiers()));
  }

  // P2가 작업마다 다시 조립하므로 협력 객체는 모두 생성자로 받는다
  @Test
  void adaptersTakeCollaboratorsThroughConstructors() throws Exception {
    assertThat(
            RetryExecutor.class.getConstructor(
                RetryPolicy.class, Sleeper.class, RetryListener.class))
        .isNotNull();
    assertThat(
            HttpGitHubClient.class.getConstructor(
                HttpClient.class, GitHubCredentials.class, RetryExecutor.class))
        .isNotNull();
    assertThat(RetryingLlmClient.class.getConstructor(LlmClient.class, RetryExecutor.class))
        .isNotNull();
    assertThat(GitHubClient.class).isAssignableFrom(HttpGitHubClient.class);
    assertThat(LlmClient.class).isAssignableFrom(RetryingLlmClient.class);
  }

  @Test
  void retryExecutorExecutesSupplierOfAttempt() throws Exception {
    Method execute = RetryExecutor.class.getMethod("execute", String.class, Supplier.class);

    assertThat(execute.getTypeParameters()).hasSize(1);
    assertThat(execute.getGenericReturnType()).isEqualTo(execute.getTypeParameters()[0]);
    assertThat(execute.getGenericParameterTypes()[1].getTypeName())
        .isEqualTo(Supplier.class.getName() + "<" + Attempt.class.getName() + "<T>>");
  }

  @Test
  void reviewEngineHasSingleConstructor() {
    assertThat(ReviewEngine.class.getConstructors()).hasSize(1);
  }

  // P2가 고치지 않고 다시 쓰는 조회 메서드
  @Test
  void gitHubClientKeepsItsReadMethods() throws Exception {
    Method getTree = GitHubClient.class.getMethod("getTree", RepoRef.class, String.class);

    assertThat(
            GitHubClient.class
                .getMethod("getPullRequest", RepoRef.class, int.class)
                .getReturnType())
        .isEqualTo(PullRequestMeta.class);
    assertThat(
            GitHubClient.class
                .getMethod("listFiles", RepoRef.class, int.class, int.class)
                .getReturnType())
        .isEqualTo(FilePage.class);
    // 잘림은 RepoTree.truncated로, 조회 실패는 예외로 알린다. Optional로 감싸지 않는다
    assertThat(getTree.getGenericReturnType()).isEqualTo(RepoTree.class);
    assertThat(
            GitHubClient.class
                .getMethod("getFile", RepoRef.class, String.class, String.class)
                .getReturnType())
        .isEqualTo(FileFetch.class);
    assertThat(
            GitHubCredentials.class.getMethod("authorizationHeader", RepoRef.class).getReturnType())
        .isEqualTo(String.class);
  }

  @Test
  void supportCallbacksKeepTheirSignatures() throws Exception {
    assertThat(
            RetryListener.class
                .getMethod(
                    "onRetry", String.class, int.class, int.class, String.class, Duration.class)
                .getReturnType())
        .isEqualTo(void.class);
    assertThat(Sleeper.class.getMethod("sleep", Duration.class).getReturnType())
        .isEqualTo(void.class);
    assertThat(WarningSink.class.getMethod("warn", Warning.class).getReturnType())
        .isEqualTo(void.class);
  }

  // T1과 T3가 구현을 기다리지 않고 쓰는 T2 순수 함수
  @Test
  void pureFunctionsUsedAcrossTracksKeepTheirSignatures() throws Exception {
    Method matches = GlobMatcher.class.getMethod("matches", String.class, String.class);
    Method validate = GlobMatcher.class.getMethod("validate", String.class);
    Method print = DiffPrinter.class.getMethod("print", List.class);
    Method of = LineRanges.class.getMethod("of", List.class);
    Method contains = LineRanges.class.getMethod("contains", int.class);
    Method apply = DiffFilter.class.getMethod("apply", List.class, Configuration.class);

    assertThat(List.of(matches, validate, print, of, apply))
        .allMatch(method -> Modifier.isStatic(method.getModifiers()));
    assertThat(Modifier.isStatic(contains.getModifiers())).isFalse();
    assertThat(matches.getReturnType()).isEqualTo(boolean.class);
    assertThat(validate.getReturnType()).isEqualTo(void.class);
    assertThat(print.getReturnType()).isEqualTo(String.class);
    assertThat(of.getReturnType()).isEqualTo(LineRanges.class);
    assertThat(contains.getReturnType()).isEqualTo(boolean.class);
    assertThat(apply.getReturnType()).isEqualTo(FilterOutcome.class);
  }
}

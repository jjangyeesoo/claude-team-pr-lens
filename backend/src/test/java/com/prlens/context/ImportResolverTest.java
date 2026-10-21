package com.prlens.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.prlens.context.FakeRepoClient.FileRequest;
import com.prlens.github.RepoTree;
import com.prlens.github.RepoTree.Entry;
import com.prlens.github.RepoTree.Kind;
import com.prlens.model.ContextFile;
import com.prlens.model.ContextSource;
import com.prlens.model.RepoRef;
import com.prlens.model.Revision;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** {@link ImportResolver} 예시 테스트 (요구사항 6.1~6.10). */
class ImportResolverTest {

  private static final RepoRef REPO = new RepoRef("acme", "shop");
  private static final String BASE = "base-sha";
  private static final RepoTree TRUNCATED = new RepoTree(List.of(), true);

  private final List<Warning> warnings = new ArrayList<>();

  private ImportResolver resolver(FakeRepoClient github, RepoTree tree) {
    BaseFileReader reader =
        new BaseFileReader(github, REPO, BASE, RepoTreeIndex.of(tree), warnings::add);
    return new ImportResolver(reader, warnings::add);
  }

  private ImportResolver resolver(FakeRepoClient github) {
    return resolver(github, TRUNCATED);
  }

  private static ContextFile claudeMd(String path, String content) {
    return new ContextFile(path, content, ContextSource.CLAUDE_MD, Revision.BASE);
  }

  private static ContextFile imported(String path, String content) {
    return new ContextFile(path, content, ContextSource.IMPORT, Revision.BASE);
  }

  private static List<String> paths(List<ContextFile> files) {
    return files.stream().map(ContextFile::path).toList();
  }

  private List<String> codes() {
    return warnings.stream().map(Warning::code).toList();
  }

  // --- scan ---

  @Test
  void scanFindsTokensAtLineStartAndAfterWhitespace() {
    assertThat(ImportResolver.scan("@a.md and @b/c.md\n\t@d"))
        .containsExactly("a.md", "b/c.md", "d");
  }

  @Test
  void scanIgnoresAtSignPrecededByNonWhitespace() {
    assertThat(ImportResolver.scan("user@example.com (@x.md) @real.md")).containsExactly("real.md");
  }

  @Test
  void scanReadsPathUntilNextWhitespace() {
    assertThat(ImportResolver.scan("@a.md, next")).containsExactly("a.md,");
    assertThat(ImportResolver.scan("@a.md\r\n@b.md\r@c.md"))
        .containsExactly("a.md", "b.md", "c.md");
  }

  @Test
  void scanSkipsFencedCodeBlocks() {
    String text = "```md\n@in.md\n```\n  ```\n@in2.md\n  ```\n@out.md";

    assertThat(ImportResolver.scan(text)).containsExactly("out.md");
  }

  @Test
  void scanTreatsUnclosedFenceAsRunningToEndOfFile() {
    assertThat(ImportResolver.scan("@before.md\n```\n@a.md\n@b.md")).containsExactly("before.md");
  }

  @Test
  void scanSkipsInlineCodeOfMatchingBacktickLength() {
    String text = "`@a.md` ``x ` @b.md`` @c.md ` @d.md";

    assertThat(ImportResolver.scan(text)).containsExactly("c.md", "d.md");
  }

  // --- resolvePath ---

  @Test
  void resolvePathIsRelativeToDeclaringFolder() {
    assertThat(ImportResolver.resolvePath("docs/CLAUDE.md", "./a/../b.md"))
        .isEqualTo(Optional.of("docs/b.md"));
    assertThat(ImportResolver.resolvePath("docs/CLAUDE.md", "../x.md"))
        .isEqualTo(Optional.of("x.md"));
    assertThat(ImportResolver.resolvePath("docs/CLAUDE.md", "a//b.md"))
        .isEqualTo(Optional.of("docs/a/b.md"));
    assertThat(ImportResolver.resolvePath("docs/CLAUDE.md", "sub\\c.md"))
        .isEqualTo(Optional.of("docs/sub/c.md"));
    assertThat(ImportResolver.resolvePath("CLAUDE.md", "docs/g.md"))
        .isEqualTo(Optional.of("docs/g.md"));
  }

  // --- resolve ---

  @Test
  void resolveFetchesImportsAtBaseAsImportSource() {
    FakeRepoClient github = new FakeRepoClient().file(BASE, "docs/guide.md", "가이드\n");

    List<ContextFile> result =
        resolver(github).resolve(List.of(claudeMd("CLAUDE.md", "@docs/guide.md\n")));

    assertThat(result).containsExactly(imported("docs/guide.md", "가이드\n"));
    assertThat(github.fileRequests()).containsExactly(new FileRequest("docs/guide.md", BASE));
  }

  @Test
  void resolveRejectsPathsOutsideRepositoryWithWarning() {
    FakeRepoClient github =
        new FakeRepoClient().file(BASE, "etc/x", "비밀").file(BASE, "x.md", "밖이 아님");
    List<String> declared =
        List.of("../x.md", "a/../../x.md", "/etc/x", "~/x.md", "C:/x.md", "c:x.md", "\\x.md");
    String content = String.join("\n", declared.stream().map(d -> "@" + d).toList());

    List<ContextFile> result = resolver(github).resolve(List.of(claudeMd("CLAUDE.md", content)));

    assertThat(result).isEmpty();
    assertThat(github.fileRequests()).isEmpty();
    assertThat(codes()).hasSize(7).containsOnly("import_outside_repo");
    for (int i = 0; i < declared.size(); i++) {
      assertThat(warnings.get(i).message()).contains("CLAUDE.md").contains(declared.get(i));
    }
  }

  private static List<ContextFile> chainStart() {
    return List.of(claudeMd("CLAUDE.md", "@d1.md"));
  }

  private static FakeRepoClient chain() {
    return new FakeRepoClient()
        .file(BASE, "d1.md", "@d2.md")
        .file(BASE, "d2.md", "@d3.md")
        .file(BASE, "d3.md", "@d4.md")
        .file(BASE, "d4.md", "@d5.md")
        .file(BASE, "d5.md", "끝");
  }

  @Test
  void resolveFollowsImportsUpToDepthFour() {
    FakeRepoClient github = chain();

    List<ContextFile> result = resolver(github).resolve(chainStart());

    assertThat(paths(result)).containsExactly("d1.md", "d2.md", "d3.md", "d4.md");
    assertThat(github.fileRequests()).doesNotContain(new FileRequest("d5.md", BASE));
  }

  @Test
  void resolveWarnsAndSkipsImportsAtDepthFiveOrMore() {
    resolver(chain()).resolve(chainStart());

    assertThat(warnings).hasSize(1);
    assertThat(warnings.get(0).code()).isEqualTo("import_depth_exceeded");
    assertThat(warnings.get(0).message()).contains("d4.md").contains("d5.md");
  }

  @Test
  void resolveSkipsAlreadyCollectedPathsAndCycles() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "a.md", "@CLAUDE.md @a.md @./a.md")
            .file(BASE, "CLAUDE.md", "요청되면 안 됨")
            .file(BASE, ".claude/rules/r.md", "요청되면 안 됨");
    List<ContextFile> collected =
        List.of(
            claudeMd("CLAUDE.md", "@a.md @.claude/rules/r.md"),
            new ContextFile(".claude/rules/r.md", "규칙", ContextSource.RULE, Revision.BASE));

    List<ContextFile> result = resolver(github).resolve(collected);

    assertThat(paths(result)).containsExactly("a.md");
    assertThat(github.fileRequests()).containsExactly(new FileRequest("a.md", BASE));
    assertThat(warnings).isEmpty();
  }

  @Test
  void resolveFollowsOnlyClaudeMdAndImportSources() {
    FakeRepoClient github =
        new FakeRepoClient()
            .file(BASE, "x.md", "x")
            .file(BASE, "z.md", "z")
            .file(BASE, "y.md", "y");
    List<ContextFile> collected =
        List.of(
            new ContextFile(".claude/rules/r.md", "@x.md", ContextSource.RULE, Revision.BASE),
            new ContextFile("docs/specs/s.md", "@z.md", ContextSource.SPEC, Revision.BASE),
            claudeMd("CLAUDE.md", "@y.md"));

    List<ContextFile> result = resolver(github).resolve(collected);

    assertThat(paths(result)).containsExactly("y.md");
    assertThat(github.fileRequests()).containsExactly(new FileRequest("y.md", BASE));
  }

  @Test
  void resolveWarnsAndSkipsMissingOrDirectoryTargets() {
    FakeRepoClient github = new FakeRepoClient().directory(BASE, "dd");
    String content = "@nf.md\n@dd\n@.\n@a/..";

    List<ContextFile> result = resolver(github).resolve(List.of(claudeMd("CLAUDE.md", content)));

    assertThat(result).isEmpty();
    assertThat(github.fileRequests())
        .containsExactly(new FileRequest("nf.md", BASE), new FileRequest("dd", BASE));
    assertThat(codes())
        .containsExactly(
            "import_not_found",
            "import_is_directory",
            "import_is_directory",
            "import_is_directory");
    List<String> raws = List.of("nf.md", "dd", ".", "a/..");
    for (int i = 0; i < raws.size(); i++) {
      assertThat(warnings.get(i).message()).contains("CLAUDE.md").contains(raws.get(i));
    }

    warnings.clear();
    FakeRepoClient treeGithub = new FakeRepoClient();
    RepoTree tree =
        new RepoTree(
            List.of(new Entry("CLAUDE.md", Kind.BLOB), new Entry("dir", Kind.TREE)), false);
    List<ContextFile> second =
        resolver(treeGithub, tree).resolve(List.of(claudeMd("CLAUDE.md", "@dir\n@gone.md")));

    assertThat(second).isEmpty();
    assertThat(treeGithub.fileRequests()).isEmpty();
    assertThat(codes()).containsExactly("import_is_directory", "import_not_found");
  }

  @Test
  void resolveRequestsEachPathOnceAndWarnsPerDeclaration() {
    FakeRepoClient github = new FakeRepoClient();
    List<ContextFile> collected =
        List.of(claudeMd("CLAUDE.md", "@gone.md"), claudeMd("docs/CLAUDE.md", "@../gone.md"));

    resolver(github).resolve(collected);

    assertThat(github.fileRequests()).containsExactly(new FileRequest("gone.md", BASE));
    assertThat(codes()).containsExactly("import_not_found", "import_not_found");
    assertThat(warnings.get(0).message()).contains("CLAUDE.md").contains("gone.md");
    assertThat(warnings.get(1).message()).contains("docs/CLAUDE.md").contains("../gone.md");
  }

  @Test
  void resolveSkipsTooLargeImportWithSingleWarning() {
    FakeRepoClient github = new FakeRepoClient().tooLarge(BASE, "big.md");

    List<ContextFile> result = resolver(github).resolve(List.of(claudeMd("CLAUDE.md", "@big.md")));

    assertThat(result).isEmpty();
    assertThat(codes()).containsExactly("context_file_too_large");
  }

  @Test
  void resolveIgnoresImportsInsideCode() {
    FakeRepoClient github = new FakeRepoClient().file(BASE, "a.md", "a").file(BASE, "b.md", "b");
    String content = "```\n@a.md\n```\n`@a.md` @b.md";

    List<ContextFile> result = resolver(github).resolve(List.of(claudeMd("CLAUDE.md", content)));

    assertThat(paths(result)).containsExactly("b.md");
    assertThat(github.fileRequests()).containsExactly(new FileRequest("b.md", BASE));
  }
}

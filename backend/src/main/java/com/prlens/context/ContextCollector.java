package com.prlens.context;

import com.prlens.filter.DiffFilter;
import com.prlens.filter.FilterOutcome;
import com.prlens.github.GitHubClient;
import com.prlens.model.ChangedFile;
import com.prlens.model.Configuration;
import com.prlens.model.PullRequestSnapshot;
import com.prlens.model.RepoRef;
import com.prlens.model.ReviewContext;
import com.prlens.support.WarningSink;
import java.util.List;

/**
 * base SHA 기준으로 팀 컨텍스트를 모은다. 순서는 CLAUDE.md, rule, import, 스펙이다. 404 외 GitHub 오류는 잡지 않고 그대로 던진다(요구사항
 * 4.11).
 */
public final class ContextCollector {

  private final GitHubClient github;
  private final WarningSink warnings;

  public ContextCollector(GitHubClient github, WarningSink warnings) {
    this.github = github;
    this.warnings = warnings;
  }

  public ReviewContext collect(PullRequestSnapshot snapshot, Configuration config) {
    RepoRef repo = snapshot.repo();
    FilterOutcome outcome = DiffFilter.apply(snapshot.files(), config);
    outcome.warnings().forEach(warnings::warn);
    List<ChangedFile> targets = outcome.targets();

    RepoTreeIndex index = RepoTreeIndex.load(github, repo, snapshot.baseSha());
    BaseFileReader reader = new BaseFileReader(github, repo, snapshot.baseSha(), index, warnings);

    ContextAccumulator acc = new ContextAccumulator();
    new ClaudeMdCollector(reader).collect(targets).forEach(acc::add);
    new RuleCollector(index, reader, warnings).collect(targets).forEach(acc::add);
    new ImportResolver(reader, warnings).resolve(acc.files()).forEach(acc::add);

    SpecLinkExtractor.Extraction links = SpecLinkExtractor.extract(snapshot.body(), repo);
    links.warnings().forEach(warnings::warn);
    new SpecCollector(github, repo, snapshot.headSha(), reader, warnings)
        .collect(links.paths(), acc.paths())
        .forEach(acc::add);
    return acc.toContext();
  }
}

package com.prlens.context;

import com.prlens.model.RepoPaths;
import com.prlens.model.RepoRef;
import com.prlens.support.Warning;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PR 본문에서 {@code docs/specs/} 아래 스펙 경로를 뽑는 순수 함수(요구사항 7.1~7.8). 상대 경로와 같은 저장소 GitHub URL 두 형태를 본문
 * 순서로 합치고, 앵커·쿼리를 떼고, 같은 경로는 처음 한 번만 남기고, 처음 20개만 돌려준다.
 */
final class SpecLinkExtractor {

  /** 서로 다른 스펙 경로의 상한(요구사항 7.7). */
  static final int MAX_PATHS = 20;

  /** 경로에 쓰는 문자. 공백, 닫는 괄호·꺾쇠, 따옴표, 백틱, 앵커(#), 쿼리(?)에서 멈춘다. */
  private static final String P = "[^\\s)\\]>\"'#?`]";

  private static final Pattern RELATIVE =
      Pattern.compile("(?<![\\w/.])(?:\\.{1,2}/)*/?docs/specs/" + P + "+");

  /** ref는 조회에 쓰지 않는다. 최소 일치라 ref 뒤 첫 {@code /docs/specs/}에서 경로가 시작한다. */
  private static final Pattern URL =
      Pattern.compile(
          "https://github\\.com/([^/\\s]+)/([^/\\s]+)/blob/" + P + "+?/(docs/specs/" + P + "+)");

  /**
   * @param paths 정리한 스펙 경로(본문 순서, 중복 없음, 최대 20개)
   * @param warnings 건너뛴 링크와 잘린 개수에 대한 경고
   */
  record Extraction(List<String> paths, List<Warning> warnings) {}

  /** 일치 하나: 본문 위치, 원래 경로, 다른 저장소 URL이면 그 owner/repo. */
  private record Hit(int start, String raw, String otherRepo) {}

  private SpecLinkExtractor() {}

  static Extraction extract(String body, RepoRef repo) {
    List<Hit> hits = new ArrayList<>();
    Matcher relative = RELATIVE.matcher(body);
    while (relative.find()) {
      hits.add(new Hit(relative.start(), relative.group(), null));
    }
    Matcher url = URL.matcher(body);
    while (url.find()) {
      boolean sameRepo =
          url.group(1).equalsIgnoreCase(repo.owner()) && url.group(2).equalsIgnoreCase(repo.name());
      hits.add(
          new Hit(url.start(), url.group(3), sameRepo ? null : url.group(1) + "/" + url.group(2)));
    }
    hits.sort(Comparator.comparingInt(Hit::start));

    List<String> distinct = new ArrayList<>();
    List<Warning> warnings = new ArrayList<>();
    for (Hit hit : hits) {
      if (hit.otherRepo() != null) {
        warnings.add(
            new Warning(
                "spec_link_other_repo",
                "다른 저장소의 스펙 링크를 건너뜁니다: " + hit.raw() + " (" + hit.otherRepo() + ")"));
        continue;
      }
      String path = clean(hit.raw());
      if (path == null) {
        warnings.add(
            new Warning(
                "spec_link_outside_specs", "docs/specs/ 밖을 가리키는 스펙 링크를 건너뜁니다: " + hit.raw()));
      } else if (!distinct.contains(path)) {
        distinct.add(path);
      }
    }

    List<String> paths = List.copyOf(distinct.subList(0, Math.min(MAX_PATHS, distinct.size())));
    int excess = distinct.size() - paths.size();
    if (excess > 0) {
      warnings.add(
          new Warning(
              "spec_links_truncated", "스펙 링크가 " + MAX_PATHS + "개를 넘어 " + excess + "개를 건너뜁니다"));
    }
    return new Extraction(paths, List.copyOf(warnings));
  }

  /** 정규화하고 빈·{@code .} 구간을 버린다. {@code ..}가 있거나 {@code docs/specs/} 아래 파일이 아니면 {@code null}. */
  private static String clean(String raw) {
    String[] segments = RepoPaths.normalize(raw).split("/", -1);
    List<String> kept = new ArrayList<>();
    for (String segment : segments) {
      if (segment.equals("..")) {
        return null;
      }
      if (!segment.isEmpty() && !segment.equals(".")) {
        kept.add(segment);
      }
    }
    if (kept.size() < 3 || !kept.get(0).equals("docs") || !kept.get(1).equals("specs")) {
      return null;
    }
    return String.join("/", kept);
  }
}

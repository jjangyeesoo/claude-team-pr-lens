package com.prlens.diff;

import com.prlens.support.PrLensException;
import java.util.Locale;

/** patch를 해석하지 못했을 때의 오류. 부분 결과는 만들지 않는다. */
public class DiffParseException extends PrLensException {

  /** 오류 종류. 이름을 소문자로 바꾼 값이 {@code PatchContent.Unparseable.errorKind}가 된다. */
  public enum Kind {
    HEADER_SYNTAX,
    LINE_COUNT_MISMATCH,
    INVALID_PREFIX,
    CONTENT_BEFORE_HEADER,
    HUNK_OVERLAP
  }

  private final String path;
  private final int lineNo;
  private final int hunkIndex;
  private final Kind kind;

  /**
   * @param lineNo 1부터 시작하는 줄 번호(줄바꿈 정규화 뒤 기준)
   * @param hunkIndex 오류가 난 Hunk의 순번(0부터). 첫 헤더 앞 내용이면 0
   */
  public DiffParseException(String path, int lineNo, int hunkIndex, Kind kind) {
    super(
        "patch를 해석할 수 없습니다: "
            + path
            + " (줄 "
            + lineNo
            + ", Hunk "
            + hunkIndex
            + ", "
            + kind.name().toLowerCase(Locale.ROOT)
            + ")");
    this.path = path;
    this.lineNo = lineNo;
    this.hunkIndex = hunkIndex;
    this.kind = kind;
  }

  public String path() {
    return path;
  }

  public int lineNo() {
    return lineNo;
  }

  public int hunkIndex() {
    return hunkIndex;
  }

  public Kind kind() {
    return kind;
  }
}

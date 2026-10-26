package com.prlens.review;

import com.prlens.support.PrLensException;
import com.prlens.support.RetriesExhaustedException;

/**
 * 분할 모드에서 모든 Chunk의 요청이 재시도를 모두 쓰고 실패했다 (요구사항 13.8). 메시지에 요구사항 21.6의 API 이름, 마지막 상태, 시도 횟수를 담기 위해
 * 마지막 실패를 cause로 둔다.
 */
public class AllChunksFailedException extends PrLensException {

  private final int failedChunks;

  /**
   * @param last 마지막으로 실패한 Chunk의 예외
   */
  public AllChunksFailedException(int failedChunks, RetriesExhaustedException last) {
    super("분할 리뷰의 Chunk " + failedChunks + "개가 모두 실패했습니다: " + last.getMessage(), last);
    this.failedChunks = failedChunks;
  }

  public int failedChunks() {
    return failedChunks;
  }
}

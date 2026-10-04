package com.example.starter.memo.domain;

public class MemoNotFoundException extends RuntimeException {

  public MemoNotFoundException(long id) {
    super("Memo not found: " + id);
  }
}

package com.prlens.support;

/** 경고를 받는 곳. CLI는 stderr 한 줄, P2는 로그로 구현한다. */
public interface WarningSink {
  void warn(Warning warning);
}

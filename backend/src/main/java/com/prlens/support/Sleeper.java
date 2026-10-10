package com.prlens.support;

import java.time.Duration;

/** 재시도 대기. 테스트에서는 실제로 기다리지 않는 가짜로 바꾼다. */
public interface Sleeper {
  void sleep(Duration duration);
}

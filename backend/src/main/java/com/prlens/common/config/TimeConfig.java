package com.prlens.common.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 시간 의존 로직은 Clock을 주입받는다. 테스트에서는 Clock.fixed(...)로 교체한다. */
@Configuration(proxyBeanMethods = false)
public class TimeConfig {

  @Bean
  public Clock clock() {
    return Clock.systemUTC();
  }
}

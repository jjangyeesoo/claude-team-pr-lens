package com.prlens.cli;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.encoder.PatternLayoutEncoder;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.ConsoleAppender;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class LogbackConfigTest {

  @Test
  void logsGoToStderrAtWarnInUtf8() throws Exception {
    URL resource = getClass().getClassLoader().getResource("logback.xml");
    assertThat(resource).isNotNull();
    LoggerContext context = new LoggerContext();
    JoranConfigurator configurator = new JoranConfigurator();
    configurator.setContext(context);
    configurator.doConfigure(resource);

    Logger root = context.getLogger("ROOT");
    assertThat(root.getLevel()).isEqualTo(Level.WARN);
    var appenders = root.iteratorForAppenders();
    assertThat(appenders.hasNext()).isTrue();
    ConsoleAppender<?> console = (ConsoleAppender<?>) appenders.next();
    assertThat(appenders.hasNext()).isFalse();
    assertThat(console.getTarget()).isEqualTo("System.err");
    PatternLayoutEncoder encoder = (PatternLayoutEncoder) console.getEncoder();
    assertThat(encoder.getCharset()).isEqualTo(StandardCharsets.UTF_8);
  }
}

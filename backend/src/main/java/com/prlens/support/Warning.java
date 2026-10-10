package com.prlens.support;

import java.util.Objects;

public record Warning(String code, String message) {
  public Warning {
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
  }
}

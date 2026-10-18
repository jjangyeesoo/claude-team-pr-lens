package com.prlens.cli;

import java.nio.file.Path;

/**
 * {@code review} 명령의 검증된 인자.
 *
 * @param config {@code --config}를 준 경우만. OS 경로 규칙대로 받은 그대로이며 해석은 설정 로딩이 한다
 */
public record ReviewArgs(String url, OutputFormat format, Path config) {}

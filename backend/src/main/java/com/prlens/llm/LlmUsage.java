package com.prlens.llm;

public record LlmUsage(
    long inputTokens, long outputTokens, long cacheWriteTokens, long cacheReadTokens) {}

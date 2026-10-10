package com.prlens.llm;

public record LlmResponse(String stopReason, String text, LlmUsage usage) {}

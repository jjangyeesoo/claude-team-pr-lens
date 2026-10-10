package com.prlens.llm;

/**
 * @param cachedUserBlock Review_Criteria_Area. 끝에 캐시 지점을 둔다
 * @param dataUserBlock Review_Data_Area
 */
public record LlmRequest(
    String model,
    int maxOutputTokens,
    String effort,
    String system,
    String cachedUserBlock,
    String dataUserBlock,
    String jsonSchema) {}

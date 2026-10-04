package com.example.starter.common.error;

import java.util.List;

/** 팀 표준 에러 응답 포맷: { code, message, details }. */
public record ErrorResponse(String code, String message, List<String> details) {}

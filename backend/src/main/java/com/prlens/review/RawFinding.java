package com.prlens.review;

import com.prlens.model.BasisType;
import com.prlens.model.Category;
import com.prlens.model.Severity;

/** 라인 판정 전의 LLM 원시 지적. 패키지 안에서만 쓴다. */
record RawFinding(
    String file,
    Integer line,
    Severity severity,
    Category category,
    String message,
    String suggestion,
    BasisType basisType,
    String basisRef) {}

package com.prlens.support;

/**
 * 재시도 정책. 대기 시간 계산은 작업 3.1에서 채운다.
 *
 * @param maxRetries 첫 시도 뒤에 다시 시도하는 횟수
 */
public record RetryPolicy(int maxRetries) {}

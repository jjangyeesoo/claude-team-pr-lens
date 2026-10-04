package com.example.starter.memo.domain;

import java.time.Instant;

public record Memo(long id, String title, String content, Instant createdAt) {}

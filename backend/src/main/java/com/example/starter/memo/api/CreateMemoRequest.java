package com.example.starter.memo.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMemoRequest(
    @NotBlank @Size(max = 100) String title, @Size(max = 2000) String content) {}

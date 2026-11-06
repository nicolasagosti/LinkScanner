package com.linkscanner.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AnalyzeRequest(@NotBlank @Size(max = 2048) String url) {
}

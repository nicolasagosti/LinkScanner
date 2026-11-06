package com.linkscanner.dtos;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param url link a analizar
 * @param deepScan opcional: pide revisarlo con los servicios externos aunque no haya señales de riesgo
 */
public record AnalyzeRequest(@NotBlank @Size(max = 2048) String url, Boolean deepScan) {
}

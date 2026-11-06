package com.linkscanner.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.linkscanner.dtos.AnalyzeRequest;
import com.linkscanner.dtos.AnalyzeResponse;
import com.linkscanner.exceptions.InvalidUrlException;
import com.linkscanner.services.ClientRateLimiter;
import com.linkscanner.services.LinkAnalysisService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1")
public class AnalyzeController {

	private final LinkAnalysisService analysisService;
	private final ClientRateLimiter rateLimiter;

	public AnalyzeController(LinkAnalysisService analysisService, ClientRateLimiter rateLimiter) {
		this.analysisService = analysisService;
		this.rateLimiter = rateLimiter;
	}

	@PostMapping("/analyze")
	public AnalyzeResponse analyze(@Valid @RequestBody AnalyzeRequest request, HttpServletRequest http) {
		if (!rateLimiter.tryAcquire(http.getRemoteAddr())) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Demasiados pedidos. Probá de nuevo en un minuto");
		}
		return analysisService.analyze(request.url(), Boolean.TRUE.equals(request.deepScan()));
	}

	@GetMapping("/analyze/{scanId}")
	public AnalyzeResponse scanResult(@PathVariable String scanId) {
		return analysisService.scanResult(scanId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "El análisis no existe o ya venció"));
	}

	@ExceptionHandler(InvalidUrlException.class)
	ProblemDetail handleInvalidUrl(InvalidUrlException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
	}

}

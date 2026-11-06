package com.linkscanner.services;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linkscanner.dtos.AnalyzeResponse;
import com.linkscanner.entities.ContentScanStatus;
import com.linkscanner.entities.Finding;
import com.linkscanner.services.ContentScanner.ScanOutcome;

/**
 * Sigue los análisis en curso en todos los servicios externos. Si cualquiera marca el link como
 * malicioso, su hallazgo sube el veredicto a DANGEROUS sin esperar al resto. Solo se puede
 * consultar un scanId creado por este backend, y no se guarda la URL.
 */
@Service
public class ContentScanService {

	private static final Logger log = LoggerFactory.getLogger(ContentScanService.class);

	private static final Duration RETENTION = Duration.ofMinutes(15);
	private static final int MAX_TRACKED_SCANS = 10_000;

	private final List<ContentScanner> scanners;
	private final Cache<String, TrackedScan> scans = CacheBuilder.newBuilder()
			.expireAfterWrite(RETENTION)
			.maximumSize(MAX_TRACKED_SCANS)
			.build();

	public ContentScanService(List<ContentScanner> scanners) {
		this.scanners = scanners;
	}

	public boolean enabled() {
		return scanners.stream().anyMatch(ContentScanner::enabled);
	}

	public AnalyzeResponse start(String url, AnalyzeResponse analysis) {
		Instant now = Instant.now();
		List<ServiceScan> services = scanners.stream()
				.filter(ContentScanner::enabled)
				.map(scanner -> submit(scanner, url, now))
				.toList();
		boolean anyRunning = services.stream().anyMatch(service -> service.status() == ContentScanStatus.PENDING);
		String scanId = anyRunning ? UUID.randomUUID().toString() : null;
		TrackedScan scan = new TrackedScan(analysis, services);
		if (anyRunning) {
			scans.put(scanId, scan);
		}
		return scan.toResponse(scanId);
	}

	/** Devuelve vacío si el scanId no existe o ya venció. */
	public Optional<AnalyzeResponse> poll(String scanId) {
		TrackedScan scan = scans.getIfPresent(scanId);
		if (scan == null) {
			return Optional.empty();
		}
		Instant now = Instant.now();
		TrackedScan updated = new TrackedScan(scan.analysis(),
				scan.services().stream().map(service -> advance(service, now)).toList());
		// Se guarda cada resultado final para no gastar cuota en más consultas.
		scans.put(scanId, updated);
		return Optional.of(updated.toResponse(scanId));
	}

	private ServiceScan submit(ContentScanner scanner, String url, Instant now) {
		try {
			String externalId = scanner.submit(url);
			return new ServiceScan(scanner, externalId, ContentScanStatus.PENDING, false, now,
					now.plus(scanner.firstPollDelay()));
		}
		catch (RestClientException ex) {
			logFailure(scanner, "pedir el análisis", ex);
			return new ServiceScan(scanner, null, ContentScanStatus.UNAVAILABLE, false, now, now);
		}
	}

	private ServiceScan advance(ServiceScan service, Instant now) {
		if (service.status() != ContentScanStatus.PENDING || now.isBefore(service.nextPollAt())) {
			return service;
		}
		return switch (check(service)) {
			case CLEAN -> service.finish(ContentScanStatus.COMPLETED, false);
			case MALICIOUS -> service.finish(ContentScanStatus.COMPLETED, true);
			case NO_VERDICT -> service.finish(ContentScanStatus.UNAVAILABLE, false);
			case PENDING -> now.isBefore(service.startedAt().plus(service.scanner().maxWait()))
					? service.pollAgainAt(now.plus(service.scanner().pollInterval()))
					: service.finish(ContentScanStatus.UNAVAILABLE, false);
		};
	}

	private ScanOutcome check(ServiceScan service) {
		try {
			return service.scanner().check(service.externalId());
		}
		catch (RestClientException ex) {
			// Puede ser pasajero (por ejemplo un 429): se reintenta hasta agotar maxWait.
			logFailure(service.scanner(), "consultar el resultado", ex);
			return ScanOutcome.PENDING;
		}
	}

	private void logFailure(ContentScanner scanner, String action, RestClientException ex) {
		// Solo el tipo de error: el mensaje podría incluir el link del usuario.
		log.warn("No se pudo {} en {}: {}", action, scanner.name(), ex.getClass().getSimpleName());
	}

	private record ServiceScan(ContentScanner scanner, String externalId, ContentScanStatus status, boolean malicious,
			Instant startedAt, Instant nextPollAt) {

		ServiceScan finish(ContentScanStatus finalStatus, boolean isMalicious) {
			return new ServiceScan(scanner, externalId, finalStatus, isMalicious, startedAt, nextPollAt);
		}

		ServiceScan pollAgainAt(Instant time) {
			return new ServiceScan(scanner, externalId, status, malicious, startedAt, time);
		}

	}

	private record TrackedScan(AnalyzeResponse analysis, List<ServiceScan> services) {

		AnalyzeResponse toResponse(String scanId) {
			Map<String, ContentScanStatus> statuses = new LinkedHashMap<>();
			List<Finding> findings = new ArrayList<>();
			for (ServiceScan service : services) {
				statuses.put(service.scanner().name(), service.status());
				if (service.malicious()) {
					findings.add(service.scanner().maliciousFinding());
				}
			}
			return analysis.withContentScan(overallStatus(), scanId, statuses, findings);
		}

		/** Solo está completo si respondieron todos: un servicio caído no alcanza para dar por limpio. */
		private ContentScanStatus overallStatus() {
			if (services.stream().anyMatch(service -> service.status() == ContentScanStatus.PENDING)) {
				return ContentScanStatus.PENDING;
			}
			return services.stream().allMatch(service -> service.status() == ContentScanStatus.COMPLETED)
					? ContentScanStatus.COMPLETED
					: ContentScanStatus.UNAVAILABLE;
		}

	}

}

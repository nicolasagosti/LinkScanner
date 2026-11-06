package com.linkscanner.services;

import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.linkscanner.config.UrlscanProperties;
import com.linkscanner.entities.Finding;

/**
 * Cliente de urlscan.io, que abre el link en un navegador aislado y analiza la página.
 */
@Component
public class UrlscanClient implements ContentScanner {

	private static final Finding MALICIOUS = new Finding("URLSCAN_MALICIOUS",
			"urlscan.io analizó la página y la marcó como maliciosa", 60);

	private final RestClient client;
	private final UrlscanProperties properties;

	public UrlscanClient(@Qualifier("urlscanRestClient") RestClient client, UrlscanProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public String name() {
		return "urlscan";
	}

	@Override
	public boolean enabled() {
		return properties.enabled();
	}

	@Override
	public Finding maliciousFinding() {
		return MALICIOUS;
	}

	@Override
	public Duration firstPollDelay() {
		return properties.firstPollDelay();
	}

	@Override
	public Duration pollInterval() {
		return properties.pollInterval();
	}

	@Override
	public Duration maxWait() {
		return properties.maxWait();
	}

	@Override
	public String submit(String url) {
		SubmitResponse response = client.post()
				.uri("/api/v1/scan")
				.contentType(MediaType.APPLICATION_JSON)
				.body(Map.of("url", url, "visibility", properties.visibility()))
				.retrieve()
				.body(SubmitResponse.class);
		if (response == null || response.uuid() == null) {
			throw new RestClientException("urlscan no devolvió un identificador de escaneo");
		}
		return response.uuid();
	}

	@Override
	public ScanOutcome check(String scanId) {
		try {
			ScanResult result = client.get()
					.uri("/api/v1/result/{scanId}/", scanId)
					.retrieve()
					.body(ScanResult.class);
			Boolean malicious = result == null ? null : result.malicious();
			if (malicious == null) {
				return ScanOutcome.NO_VERDICT;
			}
			return malicious ? ScanOutcome.MALICIOUS : ScanOutcome.CLEAN;
		}
		catch (HttpClientErrorException.NotFound ex) {
			// urlscan responde 404 hasta que el escaneo termina.
			return ScanOutcome.PENDING;
		}
	}

	record SubmitResponse(String uuid) {
	}

	record ScanResult(Verdicts verdicts) {

		Boolean malicious() {
			return verdicts == null || verdicts.overall() == null ? null : verdicts.overall().malicious();
		}

	}

	record Verdicts(Overall overall) {
	}

	record Overall(Boolean malicious) {
	}

}

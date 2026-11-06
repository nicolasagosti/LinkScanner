package com.linkscanner.services;

import java.time.Duration;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.linkscanner.config.VirusTotalProperties;
import com.linkscanner.entities.Finding;

/**
 * Cliente de VirusTotal, que consulta el link contra decenas de motores antivirus.
 * Todo lo que recibe queda visible para su comunidad, así que nunca se le manda la query ni el
 * fragmento, donde suelen ir tokens y datos personales.
 */
@Component
public class VirusTotalClient implements ContentScanner {

	private static final Finding MALICIOUS = new Finding("VIRUSTOTAL_MALICIOUS",
			"Varios motores de VirusTotal marcaron el link como malicioso", 60);
	private static final Pattern QUERY_AND_FRAGMENT = Pattern.compile("[?#].*$", Pattern.DOTALL);
	private static final String COMPLETED = "completed";

	private final RestClient client;
	private final VirusTotalProperties properties;

	public VirusTotalClient(@Qualifier("virusTotalRestClient") RestClient client, VirusTotalProperties properties) {
		this.client = client;
		this.properties = properties;
	}

	@Override
	public String name() {
		return "virustotal";
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
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("url", QUERY_AND_FRAGMENT.matcher(url).replaceFirst(""));
		SubmitResponse response = client.post()
				.uri("/api/v3/urls")
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form)
				.retrieve()
				.body(SubmitResponse.class);
		if (response == null || response.data() == null || response.data().id() == null) {
			throw new RestClientException("VirusTotal no devolvió un identificador de análisis");
		}
		return response.data().id();
	}

	@Override
	public ScanOutcome check(String scanId) {
		Analysis analysis = client.get()
				.uri("/api/v3/analyses/{scanId}", scanId)
				.retrieve()
				.body(Analysis.class);
		Attributes attributes = analysis == null || analysis.data() == null ? null : analysis.data().attributes();
		if (attributes == null) {
			return ScanOutcome.NO_VERDICT;
		}
		if (!COMPLETED.equals(attributes.status())) {
			return ScanOutcome.PENDING;
		}
		if (attributes.stats() == null || attributes.stats().malicious() == null) {
			return ScanOutcome.NO_VERDICT;
		}
		return attributes.stats().malicious() >= properties.minMaliciousEngines() ? ScanOutcome.MALICIOUS
				: ScanOutcome.CLEAN;
	}

	record SubmitResponse(SubmitData data) {
	}

	record SubmitData(String id) {
	}

	record Analysis(AnalysisData data) {
	}

	record AnalysisData(Attributes attributes) {
	}

	record Attributes(String status, Stats stats) {
	}

	record Stats(Integer malicious) {
	}

}

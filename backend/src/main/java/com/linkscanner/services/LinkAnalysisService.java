package com.linkscanner.services;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.linkscanner.config.DetectionProperties;
import com.linkscanner.dtos.AnalyzeResponse;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;
import com.linkscanner.services.heuristics.Heuristic;

@Service
public class LinkAnalysisService {

	private final UrlParser urlParser;
	private final List<Heuristic> heuristics;
	private final ContentScanService contentScans;
	private final DetectionProperties detection;

	public LinkAnalysisService(UrlParser urlParser, List<Heuristic> heuristics, ContentScanService contentScans,
			DetectionProperties detection) {
		this.urlParser = urlParser;
		this.heuristics = heuristics;
		this.contentScans = contentScans;
		this.detection = detection;
	}

	public AnalyzeResponse analyze(String rawUrl) {
		ParsedUrl url = urlParser.parse(rawUrl);
		List<Finding> findings = heuristics.stream()
				.map(heuristic -> heuristic.evaluate(url))
				.flatMap(Optional::stream)
				.toList();
		boolean official = detection.isOfficialDomain(url.registrableDomain());
		AnalyzeResponse analysis = AnalyzeResponse.of(url.unicodeHost(), official, findings);
		return shouldScanContent(url) ? contentScans.start(url.normalizedUrl(), analysis) : analysis;
	}

	public Optional<AnalyzeResponse> scanResult(String scanId) {
		return contentScans.poll(scanId);
	}

	/** Sin sufijo público (IPs, hosts internos) no se manda a terceros: podría ser una dirección interna. */
	private boolean shouldScanContent(ParsedUrl url) {
		return contentScans.enabled() && !url.publicSuffix().isEmpty();
	}

}

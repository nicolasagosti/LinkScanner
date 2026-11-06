package com.linkscanner.services;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.stereotype.Service;

import com.linkscanner.config.DetectionProperties;
import com.linkscanner.dtos.AnalyzeResponse;
import com.linkscanner.entities.ContentScanStatus;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.ParsedUrl;
import com.linkscanner.entities.Verdict;
import com.linkscanner.services.heuristics.Heuristic;

@Service
public class LinkAnalysisService {

	private final UrlParser urlParser;
	private final List<Heuristic> heuristics;
	private final ContentScanService contentScans;
	private final DetectionProperties detection;
	private final ScanBudget scanBudget;

	public LinkAnalysisService(UrlParser urlParser, List<Heuristic> heuristics, ContentScanService contentScans,
			DetectionProperties detection, ScanBudget scanBudget) {
		this.urlParser = urlParser;
		this.heuristics = heuristics;
		this.contentScans = contentScans;
		this.detection = detection;
		this.scanBudget = scanBudget;
	}

	public AnalyzeResponse analyze(String rawUrl, boolean deepScan) {
		ParsedUrl url = urlParser.parse(rawUrl);
		List<Finding> findings = heuristics.stream()
				.map(heuristic -> heuristic.evaluate(url))
				.flatMap(Optional::stream)
				.toList();
		boolean official = detection.isOfficialDomain(url.registrableDomain());
		AnalyzeResponse analysis = AnalyzeResponse.of(url.unicodeHost(), official, findings);
		boolean worthScanning = deepScan || analysis.verdict() == Verdict.SUSPICIOUS;
		if (!worthScanning || !canScanContent(url)) {
			return analysis;
		}
		return scanBudget.tryAcquire() ? contentScans.start(url.normalizedUrl(), analysis)
				: analysis.withContentScan(ContentScanStatus.UNAVAILABLE, null, Map.of(), List.of());
	}

	public Optional<AnalyzeResponse> scanResult(String scanId) {
		return contentScans.poll(scanId);
	}

	/** Sin sufijo público (IPs, hosts internos) no se manda a terceros: podría ser una dirección interna. */
	private boolean canScanContent(ParsedUrl url) {
		return contentScans.enabled() && !url.publicSuffix().isEmpty();
	}

}

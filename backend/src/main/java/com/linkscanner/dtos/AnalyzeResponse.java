package com.linkscanner.dtos;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.linkscanner.entities.ContentScanStatus;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.Verdict;

/**
 * @param contentScan estado general: solo es COMPLETED si respondieron todos los servicios
 * @param scans estado de cada servicio externo
 */
public record AnalyzeResponse(
		String host,
		Verdict verdict,
		int score,
		boolean officialDomain,
		List<Finding> findings,
		ContentScanStatus contentScan,
		Map<String, ContentScanStatus> scans,
		String scanId) {

	private static final int MAX_SCORE = 100;

	/** El resultado de las heurísticas, antes de consultar a los servicios externos. */
	public static AnalyzeResponse of(String host, boolean officialDomain, List<Finding> findings) {
		return build(host, officialDomain, findings, ContentScanStatus.SKIPPED, Map.of(), null);
	}

	public AnalyzeResponse withContentScan(ContentScanStatus status, String id, Map<String, ContentScanStatus> statuses,
			List<Finding> extraFindings) {
		return build(host, officialDomain, Stream.concat(findings.stream(), extraFindings.stream()).toList(), status,
				statuses, id);
	}

	private static AnalyzeResponse build(String host, boolean officialDomain, List<Finding> findings,
			ContentScanStatus status, Map<String, ContentScanStatus> statuses, String id) {
		List<Finding> sorted = findings.stream()
				.sorted(Comparator.comparingInt(Finding::weight).reversed())
				.toList();
		int score = Math.min(MAX_SCORE, sorted.stream().mapToInt(Finding::weight).sum());
		Verdict verdict = Verdict.from(score, officialDomain, status);
		return new AnalyzeResponse(host, verdict, score, officialDomain, sorted, status, statuses, id);
	}

}

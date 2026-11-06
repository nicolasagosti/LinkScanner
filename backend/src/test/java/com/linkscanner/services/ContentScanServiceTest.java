package com.linkscanner.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;

import com.linkscanner.dtos.AnalyzeResponse;
import com.linkscanner.entities.ContentScanStatus;
import com.linkscanner.entities.Finding;
import com.linkscanner.entities.Verdict;
import com.linkscanner.services.ContentScanner.ScanOutcome;

class ContentScanServiceTest {

	private static final Duration NONE = Duration.ZERO;
	private static final Duration LONG = Duration.ofHours(1);
	private static final String URL = "https://example.xyz/";
	private static final Finding WEAK_SIGNAL = new Finding("SUSPICIOUS_TLD", "x", 15);
	private static final AnalyzeResponse ANALYSIS = AnalyzeResponse.of("example.xyz", false, List.of(WEAK_SIGNAL));

	private ContentScanner scanner(String name, Duration firstPollDelay, Duration pollInterval, Duration maxWait) {
		ContentScanner scanner = mock(ContentScanner.class);
		when(scanner.name()).thenReturn(name);
		when(scanner.enabled()).thenReturn(true);
		when(scanner.maliciousFinding()).thenReturn(new Finding(name.toUpperCase() + "_MALICIOUS", "x", 60));
		when(scanner.firstPollDelay()).thenReturn(firstPollDelay);
		when(scanner.pollInterval()).thenReturn(pollInterval);
		when(scanner.maxWait()).thenReturn(maxWait);
		when(scanner.submit(URL)).thenReturn(name + "-id");
		return scanner;
	}

	private ContentScanner scanner(String name) {
		return scanner(name, NONE, NONE, LONG);
	}

	@Test
	void startingReturnsAnOwnIdAndKeepsHeuristics() {
		ContentScanner a = scanner("a");
		ContentScanner b = scanner("b");

		AnalyzeResponse response = new ContentScanService(List.of(a, b)).start(URL, ANALYSIS);

		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.PENDING);
		assertThat(response.scanId()).isNotBlank().isNotIn("a-id", "b-id");
		assertThat(response.score()).isEqualTo(15);
		assertThat(response.verdict()).isEqualTo(Verdict.UNKNOWN);
		assertThat(response.scans()).containsEntry("a", ContentScanStatus.PENDING)
				.containsEntry("b", ContentScanStatus.PENDING);
	}

	@Test
	void disabledServicesAreIgnored() {
		ContentScanner a = scanner("a");
		ContentScanner disabled = scanner("off");
		when(disabled.enabled()).thenReturn(false);
		when(a.check("a-id")).thenReturn(ScanOutcome.CLEAN);
		ContentScanService service = new ContentScanService(List.of(a, disabled));

		String id = service.start(URL, ANALYSIS).scanId();
		AnalyzeResponse response = service.poll(id).orElseThrow();

		verify(disabled, never()).submit(any());
		assertThat(response.scans()).containsOnlyKeys("a");
		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.COMPLETED);
	}

	@Test
	void ifEveryServiceFailsThereIsNothingToFollow() {
		ContentScanner a = scanner("a");
		when(a.submit(URL)).thenThrow(new ResourceAccessException("timeout"));

		AnalyzeResponse response = new ContentScanService(List.of(a)).start(URL, ANALYSIS);

		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.UNAVAILABLE);
		assertThat(response.scanId()).isNull();
		assertThat(response.verdict()).isEqualTo(Verdict.UNKNOWN);
	}

	@Test
	void anyMaliciousVerdictIsEnoughEvenIfTheOtherIsClean() {
		ContentScanner a = scanner("a");
		ContentScanner b = scanner("b");
		when(a.check("a-id")).thenReturn(ScanOutcome.CLEAN);
		when(b.check("b-id")).thenReturn(ScanOutcome.MALICIOUS);
		ContentScanService service = new ContentScanService(List.of(a, b));

		AnalyzeResponse response = service.poll(service.start(URL, ANALYSIS).scanId()).orElseThrow();

		assertThat(response.verdict()).isEqualTo(Verdict.DANGEROUS);
		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.COMPLETED);
		assertThat(response.score()).isEqualTo(75);
		assertThat(response.findings()).extracting(Finding::code).containsExactly("B_MALICIOUS", "SUSPICIOUS_TLD");
	}

	@Test
	void aMaliciousVerdictDoesNotWaitForTheOtherService() {
		ContentScanner a = scanner("a");
		ContentScanner b = scanner("b");
		when(a.check("a-id")).thenReturn(ScanOutcome.MALICIOUS);
		when(b.check("b-id")).thenReturn(ScanOutcome.PENDING);
		ContentScanService service = new ContentScanService(List.of(a, b));

		AnalyzeResponse response = service.poll(service.start(URL, ANALYSIS).scanId()).orElseThrow();

		assertThat(response.verdict()).isEqualTo(Verdict.DANGEROUS);
		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.PENDING);
	}

	@Test
	void bothMaliciousAddBothFindings() {
		ContentScanner a = scanner("a");
		ContentScanner b = scanner("b");
		when(a.check("a-id")).thenReturn(ScanOutcome.MALICIOUS);
		when(b.check("b-id")).thenReturn(ScanOutcome.MALICIOUS);
		ContentScanService service = new ContentScanService(List.of(a, b));

		AnalyzeResponse response = service.poll(service.start(URL, ANALYSIS).scanId()).orElseThrow();

		assertThat(response.findings()).extracting(Finding::code).contains("A_MALICIOUS", "B_MALICIOUS");
		assertThat(response.score()).isEqualTo(100);
	}

	@Test
	void itIsSafeOnlyIfAllServicesAnsweredClean() {
		ContentScanner a = scanner("a");
		ContentScanner b = scanner("b");
		when(a.check("a-id")).thenReturn(ScanOutcome.CLEAN);
		when(b.check("b-id")).thenReturn(ScanOutcome.CLEAN);
		ContentScanService service = new ContentScanService(List.of(a, b));

		AnalyzeResponse response = service.poll(service.start(URL, ANALYSIS).scanId()).orElseThrow();

		assertThat(response.verdict()).isEqualTo(Verdict.SAFE);
		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.COMPLETED);
	}

	@Test
	void oneCleanAndOneFailedIsUnknownNotSafe() {
		ContentScanner a = scanner("a");
		ContentScanner b = scanner("b");
		when(a.check("a-id")).thenReturn(ScanOutcome.CLEAN);
		when(b.submit(URL)).thenThrow(new ResourceAccessException("timeout"));
		ContentScanService service = new ContentScanService(List.of(a, b));

		AnalyzeResponse response = service.poll(service.start(URL, ANALYSIS).scanId()).orElseThrow();

		assertThat(response.verdict()).isEqualTo(Verdict.UNKNOWN);
		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.UNAVAILABLE);
		assertThat(response.scans()).containsEntry("a", ContentScanStatus.COMPLETED)
				.containsEntry("b", ContentScanStatus.UNAVAILABLE);
	}

	@Test
	void aResultWithoutVerdictIsUnavailableNotClean() {
		ContentScanner a = scanner("a");
		when(a.check("a-id")).thenReturn(ScanOutcome.NO_VERDICT);
		ContentScanService service = new ContentScanService(List.of(a));

		AnalyzeResponse response = service.poll(service.start(URL, ANALYSIS).scanId()).orElseThrow();

		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.UNAVAILABLE);
	}

	@Test
	void doesNotCallBeforeTheFirstPollDelay() {
		ContentScanner a = scanner("a", LONG, NONE, LONG);
		ContentScanService service = new ContentScanService(List.of(a));

		AnalyzeResponse response = service.poll(service.start(URL, ANALYSIS).scanId()).orElseThrow();

		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.PENDING);
		verify(a, never()).check(any());
	}

	@Test
	void respectsThePollIntervalToSaveQuota() {
		ContentScanner a = scanner("a", NONE, LONG, LONG);
		when(a.check("a-id")).thenReturn(ScanOutcome.PENDING);
		ContentScanService service = new ContentScanService(List.of(a));
		String id = service.start(URL, ANALYSIS).scanId();

		service.poll(id);
		service.poll(id);
		service.poll(id);

		verify(a, times(1)).check("a-id");
	}

	@Test
	void eachServiceKeepsItsOwnTiming() {
		ContentScanner fast = scanner("fast");
		ContentScanner slow = scanner("slow", LONG, LONG, LONG);
		when(fast.check("fast-id")).thenReturn(ScanOutcome.CLEAN);
		ContentScanService service = new ContentScanService(List.of(fast, slow));

		AnalyzeResponse response = service.poll(service.start(URL, ANALYSIS).scanId()).orElseThrow();

		assertThat(response.scans()).containsEntry("fast", ContentScanStatus.COMPLETED)
				.containsEntry("slow", ContentScanStatus.PENDING);
		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.PENDING);
		verify(slow, never()).check(any());
	}

	@Test
	void aFinishedServiceDoesNotSpendMoreQuota() {
		ContentScanner a = scanner("a");
		when(a.check("a-id")).thenReturn(ScanOutcome.CLEAN);
		ContentScanService service = new ContentScanService(List.of(a));
		String id = service.start(URL, ANALYSIS).scanId();

		service.poll(id);
		service.poll(id);

		verify(a, times(1)).check("a-id");
	}

	@Test
	void transientErrorsAreRetried() {
		ContentScanner a = scanner("a");
		when(a.check("a-id")).thenThrow(new ResourceAccessException("timeout")).thenReturn(ScanOutcome.CLEAN);
		ContentScanService service = new ContentScanService(List.of(a));
		String id = service.start(URL, ANALYSIS).scanId();

		assertThat(service.poll(id).orElseThrow().contentScan()).isEqualTo(ContentScanStatus.PENDING);
		assertThat(service.poll(id).orElseThrow().contentScan()).isEqualTo(ContentScanStatus.COMPLETED);
	}

	@Test
	void givesUpAfterMaxWait() {
		ContentScanner a = scanner("a", NONE, NONE, NONE);
		when(a.check("a-id")).thenReturn(ScanOutcome.PENDING);
		ContentScanService service = new ContentScanService(List.of(a));

		AnalyzeResponse response = service.poll(service.start(URL, ANALYSIS).scanId()).orElseThrow();

		assertThat(response.contentScan()).isEqualTo(ContentScanStatus.UNAVAILABLE);
	}

	@Test
	void unknownScanIdsNeverReachTheServices() {
		ContentScanner a = scanner("a");

		assertThat(new ContentScanService(List.of(a)).poll("../../otra-ruta")).isEmpty();
		verify(a, never()).check(any());
	}

}

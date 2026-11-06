package com.linkscanner.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.linkscanner.entities.Finding;
import com.linkscanner.services.ContentScanner;
import com.linkscanner.services.ContentScanner.ScanOutcome;
import com.linkscanner.services.UrlscanClient;
import com.linkscanner.services.VirusTotalClient;

@SpringBootTest(properties = { "linkscanner.limits.requests-per-minute-per-client=3",
		"linkscanner.limits.scans-per-minute=1" })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class LimitsFlowTest {

	private static final String SUSPICIOUS_LINK = "{\"url\":\"https://bit.ly/abc\"}";

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private UrlscanClient urlscan;

	@MockitoBean
	private VirusTotalClient virusTotal;

	private void stub(ContentScanner scanner, String name) {
		when(scanner.name()).thenReturn(name);
		when(scanner.enabled()).thenReturn(true);
		when(scanner.maliciousFinding()).thenReturn(new Finding(name, "x", 60));
		when(scanner.firstPollDelay()).thenReturn(Duration.ZERO);
		when(scanner.pollInterval()).thenReturn(Duration.ZERO);
		when(scanner.maxWait()).thenReturn(Duration.ofHours(1));
		when(scanner.submit(any())).thenReturn(name + "-id");
		when(scanner.check(any())).thenReturn(ScanOutcome.PENDING);
	}

	private ResultActions analyzeFrom(String ip) throws Exception {
		return mvc.perform(post("/api/v1/analyze").contentType(MediaType.APPLICATION_JSON).content(SUSPICIOUS_LINK)
				.with(request -> {
					request.setRemoteAddr(ip);
					return request;
				}));
	}

	@Test
	void aClientOverItsLimitGets429AndOthersAreNotAffected() throws Exception {
		for (int i = 0; i < 3; i++) {
			analyzeFrom("203.0.113.1").andExpect(status().isOk());
		}

		analyzeFrom("203.0.113.1")
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.detail").value("Demasiados pedidos. Probá de nuevo en un minuto"));
		analyzeFrom("203.0.113.2").andExpect(status().isOk());
	}

	@Test
	void withoutScanBudgetTheServicesAreNotCalledAndTheLinkIsNotReportedAsVerified() throws Exception {
		stub(urlscan, "urlscan");
		stub(virusTotal, "virustotal");

		analyzeFrom("203.0.113.1").andExpect(jsonPath("$.contentScan").value("PENDING"));
		analyzeFrom("203.0.113.2")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict").value("SUSPICIOUS"))
				.andExpect(jsonPath("$.contentScan").value("UNAVAILABLE"))
				.andExpect(jsonPath("$.scanId").doesNotExist());

		verify(urlscan, times(1)).submit(any());
		verify(virusTotal, times(1)).submit(any());
	}

}

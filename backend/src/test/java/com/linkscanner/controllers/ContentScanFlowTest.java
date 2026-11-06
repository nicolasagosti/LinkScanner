package com.linkscanner.controllers;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.client.ResourceAccessException;

import com.jayway.jsonpath.JsonPath;
import com.linkscanner.entities.Finding;
import com.linkscanner.services.ContentScanner;
import com.linkscanner.services.ContentScanner.ScanOutcome;
import com.linkscanner.services.UrlscanClient;
import com.linkscanner.services.VirusTotalClient;

@SpringBootTest
@AutoConfigureMockMvc
class ContentScanFlowTest {

	@Autowired
	private MockMvc mvc;

	@MockitoBean
	private UrlscanClient urlscan;

	@MockitoBean
	private VirusTotalClient virusTotal;

	@BeforeEach
	void setUp() {
		stub(urlscan, "urlscan", "URLSCAN_MALICIOUS", "u-id");
		stub(virusTotal, "virustotal", "VIRUSTOTAL_MALICIOUS", "v-id");
	}

	private void stub(ContentScanner scanner, String name, String findingCode, String externalId) {
		when(scanner.name()).thenReturn(name);
		when(scanner.enabled()).thenReturn(true);
		when(scanner.maliciousFinding()).thenReturn(new Finding(findingCode, "x", 60));
		when(scanner.firstPollDelay()).thenReturn(Duration.ZERO);
		when(scanner.pollInterval()).thenReturn(Duration.ZERO);
		when(scanner.maxWait()).thenReturn(Duration.ofHours(1));
		when(scanner.submit(any())).thenReturn(externalId);
		when(scanner.check(externalId)).thenReturn(ScanOutcome.PENDING);
	}

	private ResultActions analyze(String url) throws Exception {
		return mvc.perform(post("/api/v1/analyze").contentType(MediaType.APPLICATION_JSON)
				.content("{\"url\":\"" + url + "\"}"));
	}

	private String scanIdOf(String url) throws Exception {
		return JsonPath.read(analyze(url).andReturn().getResponse().getContentAsString(), "$.scanId");
	}

	private ResultActions poll(String scanId) throws Exception {
		return mvc.perform(get("/api/v1/analyze/" + scanId));
	}

	@Test
	void flowEndsSafeOnlyWhenBothServicesAnswerClean() throws Exception {
		analyze("https://promo-regalos.com/")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict").value("UNKNOWN"))
				.andExpect(jsonPath("$.contentScan").value("PENDING"))
				.andExpect(jsonPath("$.scans.urlscan").value("PENDING"))
				.andExpect(jsonPath("$.scans.virustotal").value("PENDING"));

		String id = scanIdOf("https://promo-regalos.com/");
		when(urlscan.check("u-id")).thenReturn(ScanOutcome.CLEAN);

		poll(id).andExpect(jsonPath("$.contentScan").value("PENDING"))
				.andExpect(jsonPath("$.verdict").value("UNKNOWN"))
				.andExpect(jsonPath("$.scans.urlscan").value("COMPLETED"));

		when(virusTotal.check("v-id")).thenReturn(ScanOutcome.CLEAN);

		poll(id).andExpect(jsonPath("$.contentScan").value("COMPLETED"))
				.andExpect(jsonPath("$.verdict").value("SAFE"));
	}

	@Test
	void urlscanMaliciousAloneIsDangerous() throws Exception {
		String id = scanIdOf("https://promo-regalos.com/");
		when(urlscan.check("u-id")).thenReturn(ScanOutcome.MALICIOUS);
		when(virusTotal.check("v-id")).thenReturn(ScanOutcome.CLEAN);

		poll(id).andExpect(jsonPath("$.verdict").value("DANGEROUS"))
				.andExpect(jsonPath("$.findings[0].code").value("URLSCAN_MALICIOUS"));
	}

	@Test
	void virusTotalMaliciousAloneIsDangerous() throws Exception {
		String id = scanIdOf("https://promo-regalos.com/");
		when(urlscan.check("u-id")).thenReturn(ScanOutcome.CLEAN);
		when(virusTotal.check("v-id")).thenReturn(ScanOutcome.MALICIOUS);

		poll(id).andExpect(jsonPath("$.verdict").value("DANGEROUS"))
				.andExpect(jsonPath("$.findings[0].code").value("VIRUSTOTAL_MALICIOUS"));
	}

	@Test
	void sendsTheNormalizedUrlToBothServices() throws Exception {
		analyze("HTTPS://Example.com/a?b=C");

		verify(urlscan).submit("https://example.com/a?b=C");
		verify(virusTotal).submit("https://example.com/a?b=C");
	}

	@ParameterizedTest
	@ValueSource(strings = { "http://192.168.0.1/", "http://localhost/" })
	void doesNotSendNonPublicHosts(String url) throws Exception {
		analyze(url)
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.contentScan").value("SKIPPED"))
				.andExpect(jsonPath("$.scanId").doesNotExist());

		verify(urlscan, never()).submit(any());
		verify(virusTotal, never()).submit(any());
	}

	@Test
	void dangerousLinksAreScannedAndStayDangerous() throws Exception {
		analyze("https://rnercadopago.com/")
				.andExpect(jsonPath("$.verdict").value("DANGEROUS"))
				.andExpect(jsonPath("$.contentScan").value("PENDING"));

		verify(urlscan).submit("https://rnercadopago.com/");
		verify(virusTotal).submit("https://rnercadopago.com/");
	}

	@Test
	void credentialsAreNotSentToTheServices() throws Exception {
		analyze("https://usuario:clave@example.com/").andExpect(jsonPath("$.verdict").value("DANGEROUS"));

		verify(urlscan).submit("https://example.com/");
		verify(virusTotal).submit("https://example.com/");
	}

	@Test
	void officialDomainsStaySafeWhileWaiting() throws Exception {
		analyze("https://www.mercadopago.com.ar/")
				.andExpect(jsonPath("$.verdict").value("SAFE"))
				.andExpect(jsonPath("$.officialDomain").value(true))
				.andExpect(jsonPath("$.contentScan").value("PENDING"));
	}

	@Test
	void officialDomainsStaySafeIfTheServicesAreDown() throws Exception {
		when(urlscan.submit(any())).thenThrow(new ResourceAccessException("timeout"));
		when(virusTotal.submit(any())).thenThrow(new ResourceAccessException("timeout"));

		analyze("https://www.mercadopago.com.ar/")
				.andExpect(jsonPath("$.verdict").value("SAFE"))
				.andExpect(jsonPath("$.contentScan").value("UNAVAILABLE"));
	}

	@Test
	void anOfficialDomainMarkedMaliciousBecomesDangerous() throws Exception {
		String id = scanIdOf("https://www.mercadopago.com.ar/");
		when(virusTotal.check("v-id")).thenReturn(ScanOutcome.MALICIOUS);

		poll(id).andExpect(jsonPath("$.verdict").value("DANGEROUS"))
				.andExpect(jsonPath("$.findings[0].code").value("VIRUSTOTAL_MALICIOUS"));
	}

	@Test
	void stillAnswersWhenBothServicesAreDown() throws Exception {
		when(urlscan.submit(any())).thenThrow(new ResourceAccessException("timeout"));
		when(virusTotal.submit(any())).thenThrow(new ResourceAccessException("timeout"));

		analyze("https://bit.ly/abc")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict").value("SUSPICIOUS"))
				.andExpect(jsonPath("$.contentScan").value("UNAVAILABLE"));
	}

	@Test
	void oneServiceDownStillFollowsTheOther() throws Exception {
		when(virusTotal.submit(any())).thenThrow(new ResourceAccessException("timeout"));

		analyze("https://promo-regalos.com/")
				.andExpect(jsonPath("$.contentScan").value("PENDING"))
				.andExpect(jsonPath("$.scans.urlscan").value("PENDING"))
				.andExpect(jsonPath("$.scans.virustotal").value("UNAVAILABLE"));
	}

	@Test
	void unknownScanIdIsNotFound() throws Exception {
		poll("no-existe").andExpect(status().isNotFound());
		verify(urlscan, never()).check(any());
	}

}

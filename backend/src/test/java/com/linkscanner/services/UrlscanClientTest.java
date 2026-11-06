package com.linkscanner.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.linkscanner.config.ScannerClientsConfig;
import com.linkscanner.config.UrlscanProperties;
import com.linkscanner.services.ContentScanner.ScanOutcome;

class UrlscanClientTest {

	private static final String RESULT_URL = "https://urlscan.io/api/v1/result/abc/";

	private MockRestServiceServer server;
	private UrlscanClient client;

	@BeforeEach
	void setUp() {
		UrlscanProperties properties = new UrlscanProperties("test-key", "https://urlscan.io", "private",
				Duration.ofSeconds(5), Duration.ZERO, Duration.ofSeconds(2), Duration.ofMinutes(2));
		RestClient.Builder builder = new ScannerClientsConfig().urlscanRestClient(properties).mutate();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new UrlscanClient(builder.build(), properties);
	}

	@Test
	void submitsTheScanWithKeyAndVisibility() {
		server.expect(requestTo("https://urlscan.io/api/v1/scan"))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("API-Key", "test-key"))
				.andExpect(jsonPath("$.url").value("https://example.com/?a=1"))
				.andExpect(jsonPath("$.visibility").value("private"))
				.andRespond(withSuccess("""
						{"uuid":"abc","visibility":"private","country":"de","extra":1}""",
						MediaType.APPLICATION_JSON));

		assertThat(client.submit("https://example.com/?a=1")).isEqualTo("abc");
		server.verify();
	}

	@Test
	void failsIfTheSubmissionHasNoId() {
		server.expect(requestTo("https://urlscan.io/api/v1/scan"))
				.andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.submit("https://example.com/")).isInstanceOf(RestClientException.class);
	}

	@Test
	void readsAMaliciousVerdictIgnoringOtherFields() {
		server.expect(requestTo(RESULT_URL)).andRespond(withSuccess("""
				{"page":{"country":"AR"},"verdicts":{"overall":{"score":100,"malicious":true},"urlscan":{"score":100}}}""",
				MediaType.APPLICATION_JSON));

		assertThat(client.check("abc")).isEqualTo(ScanOutcome.MALICIOUS);
	}

	@Test
	void readsACleanVerdict() {
		server.expect(requestTo(RESULT_URL)).andRespond(withSuccess("""
				{"verdicts":{"overall":{"malicious":false}}}""", MediaType.APPLICATION_JSON));

		assertThat(client.check("abc")).isEqualTo(ScanOutcome.CLEAN);
	}

	@Test
	void aMissingVerdictIsNotTheSameAsClean() {
		server.expect(requestTo(RESULT_URL)).andRespond(withSuccess("{\"page\":{}}", MediaType.APPLICATION_JSON));

		assertThat(client.check("abc")).isEqualTo(ScanOutcome.NO_VERDICT);
	}

	@Test
	void notFoundMeansTheScanIsStillRunning() {
		server.expect(requestTo(RESULT_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

		assertThat(client.check("abc")).isEqualTo(ScanOutcome.PENDING);
	}

	@Test
	void rateLimitIsAnError() {
		server.expect(requestTo(RESULT_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> client.check("abc")).isInstanceOf(RestClientException.class);
	}

}

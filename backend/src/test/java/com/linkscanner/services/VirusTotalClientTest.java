package com.linkscanner.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.linkscanner.config.ScannerClientsConfig;
import com.linkscanner.config.VirusTotalProperties;
import com.linkscanner.services.ContentScanner.ScanOutcome;

class VirusTotalClientTest {

	private static final String SCAN_URL = "https://www.virustotal.com/api/v3/urls";
	private static final String ANALYSIS_URL = "https://www.virustotal.com/api/v3/analyses/abc";

	private MockRestServiceServer server;
	private VirusTotalClient client;

	@BeforeEach
	void setUp() {
		VirusTotalProperties properties = new VirusTotalProperties("test-key", "https://www.virustotal.com",
				Duration.ofSeconds(5), Duration.ZERO, Duration.ofSeconds(20), Duration.ofMinutes(3), 2);
		RestClient.Builder builder = new ScannerClientsConfig().virusTotalRestClient(properties).mutate();
		server = MockRestServiceServer.bindTo(builder).build();
		client = new VirusTotalClient(builder.build(), properties);
	}

	private String analysis(String status, int malicious) {
		return """
				{"data":{"id":"abc","type":"analysis","attributes":{"status":"%s","date":1,
				"stats":{"harmless":60,"malicious":%d,"suspicious":0,"undetected":10,"timeout":0}}}}"""
				.formatted(status, malicious);
	}

	@Test
	void submitsAsAFormWithTheKey() {
		server.expect(requestTo(SCAN_URL))
				.andExpect(method(HttpMethod.POST))
				.andExpect(header("x-apikey", "test-key"))
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
				.andExpect(content().string("url=https%3A%2F%2Fexample.com%2Fa"))
				.andRespond(withSuccess("""
						{"data":{"type":"analysis","id":"abc","links":{"self":"x"}}}""", MediaType.APPLICATION_JSON));

		assertThat(client.submit("https://example.com/a")).isEqualTo("abc");
		server.verify();
	}

	@ParameterizedTest
	@CsvSource({
			"https://example.com/a?email=yo@mail.com&token=123, url=https%3A%2F%2Fexample.com%2Fa",
			"https://example.com/a#email=yo@mail.com, url=https%3A%2F%2Fexample.com%2Fa",
			"https://example.com/?a=1#b, url=https%3A%2F%2Fexample.com%2F",
	})
	void neverSendsTheQueryOrFragment(String url, String expectedBody) {
		server.expect(requestTo(SCAN_URL))
				.andExpect(content().string(expectedBody))
				.andRespond(withSuccess("{\"data\":{\"id\":\"abc\"}}", MediaType.APPLICATION_JSON));

		client.submit(url);
		server.verify();
	}

	@Test
	void failsIfTheSubmissionHasNoId() {
		server.expect(requestTo(SCAN_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

		assertThatThrownBy(() -> client.submit("https://example.com/")).isInstanceOf(RestClientException.class);
	}

	@ParameterizedTest
	@CsvSource({
			"queued, 0, PENDING",
			"in-progress, 5, PENDING",
			"completed, 0, CLEAN",
			"completed, 1, CLEAN",
			"completed, 2, MALICIOUS",
			"completed, 30, MALICIOUS",
	})
	void readsTheAnalysis(String status, int malicious, ScanOutcome expected) {
		server.expect(requestTo(ANALYSIS_URL))
				.andExpect(header("x-apikey", "test-key"))
				.andRespond(withSuccess(analysis(status, malicious), MediaType.APPLICATION_JSON));

		assertThat(client.check("abc")).isEqualTo(expected);
	}

	@Test
	void aCompletedAnalysisWithoutStatsIsNotClean() {
		server.expect(requestTo(ANALYSIS_URL)).andRespond(withSuccess("""
				{"data":{"attributes":{"status":"completed"}}}""", MediaType.APPLICATION_JSON));

		assertThat(client.check("abc")).isEqualTo(ScanOutcome.NO_VERDICT);
	}

	@Test
	void rateLimitIsAnError() {
		server.expect(requestTo(ANALYSIS_URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

		assertThatThrownBy(() -> client.check("abc")).isInstanceOf(RestClientException.class);
	}

}

package com.linkscanner.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class ScannerClientsConfig {

	@Bean
	public RestClient urlscanRestClient(UrlscanProperties properties) {
		return client(properties.baseUrl(), "API-Key", properties.apiKey(), properties.timeout());
	}

	@Bean
	public RestClient virusTotalRestClient(VirusTotalProperties properties) {
		return client(properties.baseUrl(), "x-apikey", properties.apiKey(), properties.timeout());
	}

	private RestClient client(String baseUrl, String keyHeader, String apiKey, Duration timeout) {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(timeout);
		requestFactory.setReadTimeout(timeout);
		return RestClient.builder()
				.baseUrl(baseUrl)
				.defaultHeader(keyHeader, apiKey)
				.requestFactory(requestFactory)
				.build();
	}

}

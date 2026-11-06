package com.linkscanner.services;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import com.linkscanner.config.LimitsProperties;

class ClientRateLimiterTest {

	private static final String HEADER = "X-Real-IP";

	private ClientRateLimiter limiter(String clientIpHeader) {
		return new ClientRateLimiter(new LimitsProperties(1, 1, 1, clientIpHeader));
	}

	private MockHttpServletRequest request(String remoteAddr, String headerValue) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(remoteAddr);
		if (headerValue != null) {
			request.addHeader(HEADER, headerValue);
		}
		return request;
	}

	@Test
	void behindAProxyTheClientIsTheHeaderEvenIfTheProxyAddressChanges() {
		ClientRateLimiter limiter = limiter(HEADER);

		assertThat(limiter.tryAcquire(request("10.0.0.1", "203.0.113.1"))).isTrue();
		assertThat(limiter.tryAcquire(request("10.0.0.2", "203.0.113.1"))).isFalse();
		assertThat(limiter.tryAcquire(request("10.0.0.1", "203.0.113.2"))).isTrue();
	}

	@Test
	void withoutAConfiguredHeaderAForgedOneIsIgnored() {
		ClientRateLimiter limiter = limiter("");

		assertThat(limiter.tryAcquire(request("203.0.113.1", "198.51.100.1"))).isTrue();
		assertThat(limiter.tryAcquire(request("203.0.113.1", "198.51.100.2"))).isFalse();
	}

	@Test
	void ifTheProxyDoesNotSendTheHeaderItFallsBackToTheRemoteAddress() {
		ClientRateLimiter limiter = limiter(HEADER);

		assertThat(limiter.tryAcquire(request("203.0.113.1", null))).isTrue();
		assertThat(limiter.tryAcquire(request("203.0.113.1", null))).isFalse();
	}

}

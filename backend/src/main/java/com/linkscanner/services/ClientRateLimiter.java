package com.linkscanner.services;

import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.linkscanner.config.LimitsProperties;

import jakarta.servlet.http.HttpServletRequest;

/** Limita cuántos análisis puede pedir cada cliente por minuto. Se cuenta en memoria, por instancia. */
@Component
public class ClientRateLimiter {

	private static final int MAX_TRACKED_CLIENTS = 100_000;

	private final int limit;
	private final String clientIpHeader;
	private final Cache<String, AtomicInteger> counts = CacheBuilder.newBuilder()
			.expireAfterWrite(Duration.ofMinutes(1))
			.maximumSize(MAX_TRACKED_CLIENTS)
			.build();

	public ClientRateLimiter(LimitsProperties limits) {
		this.limit = limits.requestsPerMinutePerClient();
		this.clientIpHeader = limits.clientIpHeader();
	}

	public boolean tryAcquire(HttpServletRequest request) {
		try {
			return counts.get(clientOf(request), AtomicInteger::new).incrementAndGet() <= limit;
		}
		catch (ExecutionException ex) {
			throw new IllegalStateException(ex);
		}
	}

	private String clientOf(HttpServletRequest request) {
		String fromProxy = StringUtils.hasText(clientIpHeader) ? request.getHeader(clientIpHeader) : null;
		return StringUtils.hasText(fromProxy) ? fromProxy : request.getRemoteAddr();
	}

}

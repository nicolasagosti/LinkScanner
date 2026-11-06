package com.linkscanner.services;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.linkscanner.config.LimitsProperties;

/**
 * Tope global de escaneos externos, para no agotar la cuota de los servicios aunque los pedidos
 * lleguen desde muchas IP. Se cuenta en memoria, por instancia, y se reinicia con el backend.
 */
@Component
public class ScanBudget {

	private final Clock clock;
	private final Window perMinute;
	private final Window perDay;

	@Autowired
	public ScanBudget(LimitsProperties limits) {
		this(limits, Clock.systemUTC());
	}

	ScanBudget(LimitsProperties limits, Clock clock) {
		this.clock = clock;
		this.perMinute = new Window(Duration.ofMinutes(1), limits.scansPerMinute());
		this.perDay = new Window(Duration.ofDays(1), limits.scansPerDay());
	}

	public synchronized boolean tryAcquire() {
		Instant now = clock.instant();
		if (!perMinute.hasRoom(now) || !perDay.hasRoom(now)) {
			return false;
		}
		perMinute.count++;
		perDay.count++;
		return true;
	}

	private static final class Window {

		private final Duration duration;
		private final int limit;
		private Instant start = Instant.MIN;
		private int count;

		Window(Duration duration, int limit) {
			this.duration = duration;
			this.limit = limit;
		}

		boolean hasRoom(Instant now) {
			if (start.equals(Instant.MIN) || !now.isBefore(start.plus(duration))) {
				start = now;
				count = 0;
			}
			return count < limit;
		}

	}

}

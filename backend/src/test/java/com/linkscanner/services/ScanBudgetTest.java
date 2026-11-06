package com.linkscanner.services;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.linkscanner.config.LimitsProperties;

class ScanBudgetTest {

	private static final class MovableClock extends Clock {

		private Instant now = Instant.parse("2026-01-01T00:00:00Z");

		void advance(Duration duration) {
			now = now.plus(duration);
		}

		@Override
		public Instant instant() {
			return now;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

	}

	private final MovableClock clock = new MovableClock();

	private ScanBudget budget(int perMinute, int perDay) {
		return new ScanBudget(new LimitsProperties(1, perMinute, perDay), clock);
	}

	@Test
	void stopsAtTheMinuteLimitAndRecoversInTheNextMinute() {
		ScanBudget budget = budget(2, 100);

		assertThat(budget.tryAcquire()).isTrue();
		assertThat(budget.tryAcquire()).isTrue();
		assertThat(budget.tryAcquire()).isFalse();

		clock.advance(Duration.ofMinutes(1));
		assertThat(budget.tryAcquire()).isTrue();
	}

	@Test
	void theDailyLimitHoldsAcrossMinutesUntilTheNextDay() {
		ScanBudget budget = budget(100, 2);

		assertThat(budget.tryAcquire()).isTrue();
		clock.advance(Duration.ofMinutes(5));
		assertThat(budget.tryAcquire()).isTrue();
		clock.advance(Duration.ofMinutes(5));
		assertThat(budget.tryAcquire()).isFalse();

		clock.advance(Duration.ofDays(1));
		assertThat(budget.tryAcquire()).isTrue();
	}

	@Test
	void aRejectedScanDoesNotSpendTheOtherWindow() {
		ScanBudget budget = budget(1, 2);

		assertThat(budget.tryAcquire()).isTrue();
		assertThat(budget.tryAcquire()).isFalse();
		assertThat(budget.tryAcquire()).isFalse();

		clock.advance(Duration.ofMinutes(1));
		assertThat(budget.tryAcquire()).isTrue();
	}

}

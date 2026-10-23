package io.grouploom.backend.email;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

class EmailOutboxEntryTest {

	private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
	private static final int MAX_ATTEMPTS = 5;

	private EmailOutboxEntry entry() {
		return new EmailOutboxEntry(null, null, null, EmailKind.PREFERENCES_LINK, NOW);
	}

	@Test
	void new_entry_isPendingAndDueImmediately() {
		EmailOutboxEntry e = entry();

		assertThat(e.getStatus()).isEqualTo(EmailStatus.PENDING);
		assertThat(e.getAttempts()).isZero();
		assertThat(e.getNextAttemptAt()).isEqualTo(NOW);
		assertThat(e.getCreatedAt()).isEqualTo(NOW);
	}

	@Test
	void claim_marksSendingAndSetsClaimDeadline() {
		EmailOutboxEntry e = entry();

		e.claim(NOW, Duration.ofMinutes(2));

		assertThat(e.getStatus()).isEqualTo(EmailStatus.SENDING);
		assertThat(e.getClaimedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
	}

	@Test
	void markSent_recordsTimeAndClearsClaimAndError() {
		EmailOutboxEntry e = entry();
		e.claim(NOW, Duration.ofMinutes(2));
		e.scheduleRetry(NOW, "boom", MAX_ATTEMPTS);
		e.claim(NOW, Duration.ofMinutes(2));

		e.markSent(NOW.plusSeconds(5));

		assertThat(e.getStatus()).isEqualTo(EmailStatus.SENT);
		assertThat(e.getSentAt()).isEqualTo(NOW.plusSeconds(5));
		assertThat(e.getClaimedUntil()).isNull();
		assertThat(e.getLastError()).isNull();
		assertThat(e.getAttempts()).isEqualTo(2);
	}

	@Test
	void scheduleRetry_backsOffOneTwoFourEightMinutes() {
		EmailOutboxEntry e = entry();
		long[] expectedMinutes = { 1, 2, 4, 8 };

		for (int i = 0; i < expectedMinutes.length; i++) {
			e.claim(NOW, Duration.ofMinutes(2));
			e.scheduleRetry(NOW, "transient", MAX_ATTEMPTS);

			assertThat(e.getStatus()).isEqualTo(EmailStatus.PENDING);
			assertThat(e.getAttempts()).isEqualTo(i + 1);
			assertThat(e.getNextAttemptAt()).isEqualTo(NOW.plus(Duration.ofMinutes(expectedMinutes[i])));
			assertThat(e.getClaimedUntil()).isNull();
			assertThat(e.getLastError()).isEqualTo("transient");
		}
	}

	@Test
	void scheduleRetry_whenAttemptsAreUsedUp_failsPermanently() {
		EmailOutboxEntry e = entry();
		for (int i = 0; i < MAX_ATTEMPTS; i++) {
			e.claim(NOW, Duration.ofMinutes(2));
			e.scheduleRetry(NOW, "still down", MAX_ATTEMPTS);
		}

		assertThat(e.getStatus()).isEqualTo(EmailStatus.FAILED);
		assertThat(e.getAttempts()).isEqualTo(MAX_ATTEMPTS);
		assertThat(e.getLastError()).isEqualTo("still down");
	}

	@Test
	void fail_failsImmediatelyAndCountsTheAttempt() {
		EmailOutboxEntry e = entry();
		e.claim(NOW, Duration.ofMinutes(2));

		e.fail(NOW, "bad address");

		assertThat(e.getStatus()).isEqualTo(EmailStatus.FAILED);
		assertThat(e.getAttempts()).isEqualTo(1);
		assertThat(e.getClaimedUntil()).isNull();
		assertThat(e.getLastError()).isEqualTo("bad address");
	}

	@Test
	void scheduleRetry_truncatesLongErrorsTo500Characters() {
		EmailOutboxEntry e = entry();

		e.fail(NOW, "x".repeat(900));

		assertThat(e.getLastError()).hasSize(500);
	}

}

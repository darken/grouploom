package io.grouploom.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.grouploom.backend.config.TestProperties;

@ExtendWith(MockitoExtension.class)
class NotificationFanOutTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");

	@Mock EventNotificationRepository notifications;
	@Mock NotificationFanOutWorker worker;

	private NotificationFanOut fanOut() {
		return new NotificationFanOut(notifications, worker, TestProperties.app("http://localhost", Duration.ofDays(60)),
				Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void fanOutDue_claimsUsingTheConfiguredBatchSizeAndTimeout() {
		given(notifications.claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20)).willReturn(List.of());

		assertThat(fanOut().fanOutDue()).isZero();

		verify(notifications).claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20);
	}

	@Test
	void fanOutDue_processesEveryClaimedNotification() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		given(notifications.claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20)).willReturn(List.of(a, b));

		assertThat(fanOut().fanOutDue()).isEqualTo(2);

		verify(worker).process(a);
		verify(worker).process(b);
	}

	@Test
	void fanOutDue_whenOneFails_stillProcessesTheOthers() {
		UUID failing = UUID.randomUUID();
		UUID healthy = UUID.randomUUID();
		given(notifications.claimDue(NOW, NOW.plus(Duration.ofMinutes(2)), 20)).willReturn(List.of(failing, healthy));
		doThrow(new IllegalStateException("boom")).when(worker).process(failing);

		assertThat(fanOut().fanOutDue()).isEqualTo(2);

		verify(worker).process(healthy);
	}

}

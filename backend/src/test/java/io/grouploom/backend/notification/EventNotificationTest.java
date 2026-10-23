package io.grouploom.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import io.grouploom.backend.event.Event;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.group.Group;

class EventNotificationTest {

	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant START = Instant.parse("2026-06-10T10:00:00Z");

	private final Group group = new Group("Club", NOW);
	private final EventType type = new EventType(group, "Practice", null, EventTypeColor.GREEN, null, NOW);
	private final Event event = new Event(group, type, "Practice", null, null, START, START.plusSeconds(3600), "UTC", NOW);

	private EventNotification notification(Integer offsetMinutes, Instant sendAt) {
		return new EventNotification(event, group, NotificationKind.REMINDER, sendAt, offsetMinutes, null, null, NOW);
	}

	@Test
	void new_isScheduled() {
		assertThat(notification(null, NOW).getStatus()).isEqualTo(NotificationStatus.SCHEDULED);
	}

	@Test
	void reschedule_withOffset_recalculatesFromTheNewStart() {
		EventNotification n = notification(1440, START.minus(Duration.ofMinutes(1440)));
		Instant newStart = START.plus(Duration.ofDays(2));

		n.reschedule(newStart, NOW);

		assertThat(n.getSendAt()).isEqualTo(newStart.minus(Duration.ofMinutes(1440)));
	}

	@Test
	void reschedule_whenTheNewSendTimeIsPast_makesItDueNow() {
		EventNotification n = notification(1440, START.minus(Duration.ofMinutes(1440)));
		Instant newStart = NOW.plus(Duration.ofHours(2));

		n.reschedule(newStart, NOW);

		assertThat(n.getSendAt()).isEqualTo(NOW);
	}

	@Test
	void reschedule_withoutOffset_leavesTheSendTimeAlone() {
		Instant at = START.minus(Duration.ofHours(5));
		EventNotification n = notification(null, at);

		n.reschedule(START.plus(Duration.ofDays(1)), NOW);

		assertThat(n.getSendAt()).isEqualTo(at);
	}

	@Test
	void reschedule_whenNotScheduled_doesNothing() {
		EventNotification n = notification(1440, START.minus(Duration.ofMinutes(1440)));
		n.claim(NOW, Duration.ofMinutes(2));
		Instant before = n.getSendAt();

		n.reschedule(START.plus(Duration.ofDays(1)), NOW);

		assertThat(n.getSendAt()).isEqualTo(before);
	}

	@Test
	void claim_marksSendingUntilTheTimeout() {
		EventNotification n = notification(null, NOW);

		n.claim(NOW, Duration.ofMinutes(2));

		assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENDING);
		assertThat(n.getClaimedUntil()).isEqualTo(NOW.plus(Duration.ofMinutes(2)));
	}

	@Test
	void markFannedOut_recordsCountAndTimeAndReleasesTheClaim() {
		EventNotification n = notification(null, NOW);
		n.claim(NOW, Duration.ofMinutes(2));

		n.markFannedOut(18, NOW.plusSeconds(5));

		assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(n.getRecipientCount()).isEqualTo(18);
		assertThat(n.getSentAt()).isEqualTo(NOW.plusSeconds(5));
		assertThat(n.getClaimedUntil()).isNull();
	}

	@Test
	void cancel_whenScheduledOrSending_marksCancelled() {
		EventNotification scheduled = notification(null, NOW);
		EventNotification sending = notification(null, NOW);
		sending.claim(NOW, Duration.ofMinutes(2));

		scheduled.cancel();
		sending.cancel();

		assertThat(scheduled.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
		assertThat(sending.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
	}

	@Test
	void cancel_whenAlreadySent_throws() {
		EventNotification n = notification(null, NOW);
		n.markFannedOut(1, NOW);

		assertThatThrownBy(n::cancel).isInstanceOf(IllegalStateException.class);
	}

}

package io.grouploom.backend.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import io.grouploom.backend.email.EmailOutboxRepository;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeColor;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationFanOutWorkerTest {

	private static final Instant CREATED = Instant.parse("2026-01-01T00:00:00Z");
	private static final Instant NOW = Instant.parse("2026-06-01T12:00:00Z");
	private static final Instant START = Instant.parse("2026-06-10T10:00:00Z");

	@Mock EventNotificationRepository notifications;
	@Mock EventRepository events;
	@Mock EmailOutboxRepository outbox;

	private Group group;
	private User organizer;
	private EventType type;
	private Event event;
	private NotificationFanOutWorker worker;

	@BeforeEach
	void setUp() {
		group = new Group("Club", CREATED);
		ReflectionTestUtils.setField(group, "id", UUID.randomUUID());
		organizer = new User(group, "olga@example.com", "Olga", "hash", Role.OWNER, CREATED);
		ReflectionTestUtils.setField(organizer, "id", UUID.randomUUID());
		type = new EventType(group, "Practice", null, EventTypeColor.GREEN, null, CREATED);
		ReflectionTestUtils.setField(type, "id", UUID.randomUUID());
		event = new Event(group, type, "Practice", null, null, START, START.plus(Duration.ofHours(1)), "UTC", CREATED);
		ReflectionTestUtils.setField(event, "id", UUID.randomUUID());
		given(events.findByIdForShare(event.getId())).willReturn(Optional.of(event));
		worker = new NotificationFanOutWorker(notifications, events, outbox, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private EventNotification claimed(NotificationKind kind, User createdBy) {
		EventNotification n = new EventNotification(event, group, kind, NOW, null, null, createdBy, CREATED);
		ReflectionTestUtils.setField(n, "id", UUID.randomUUID());
		n.claim(NOW, Duration.ofMinutes(2));
		given(notifications.findById(n.getId())).willReturn(Optional.of(n));
		return n;
	}

	@Test
	void process_aDueInvitation_fansOutAndIsMarkedSentWithTheCount() {
		EventNotification n = claimed(NotificationKind.INVITATION, organizer);
		given(outbox.fanOutInvitation(n.getId(), event.getId(), group.getId(), type.getId(), "EVENT_INVITATION",
				organizer.getId(), NOW)).willReturn(18);

		worker.process(n.getId());

		assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(n.getRecipientCount()).isEqualTo(18);
		assertThat(n.getSentAt()).isEqualTo(NOW);
	}

	@Test
	void process_aReminder_queuesReminderEmails() {
		EventNotification n = claimed(NotificationKind.REMINDER, organizer);
		given(outbox.fanOutInvitation(any(), any(), any(), any(), eq("EVENT_REMINDER"), any(), any())).willReturn(3);

		worker.process(n.getId());

		assertThat(n.getRecipientCount()).isEqualTo(3);
	}

	@Test
	void process_aCancellation_queuesCancellationsEvenForACancelledEvent() {
		event.cancel("Closed", NOW);
		EventNotification n = claimed(NotificationKind.CANCELLATION, organizer);
		given(outbox.fanOutCancellation(n.getId(), event.getId(), group.getId(), organizer.getId(), NOW)).willReturn(2);

		worker.process(n.getId());

		assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(n.getRecipientCount()).isEqualTo(2);
	}

	@Test
	void process_anInvitationForACancelledEvent_isCancelledWithoutQueueingAnything() {
		event.cancel(null, NOW);
		EventNotification n = claimed(NotificationKind.INVITATION, organizer);

		worker.process(n.getId());

		assertThat(n.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
		verifyNoInteractions(outbox);
	}

	@Test
	void process_withNoOneToNotify_isSentWithZeroRecipients() {
		EventNotification n = claimed(NotificationKind.INVITATION, organizer);

		worker.process(n.getId());

		assertThat(n.getStatus()).isEqualTo(NotificationStatus.SENT);
		assertThat(n.getRecipientCount()).isZero();
	}

	@Test
	void process_whenTheCreatorIsGone_queuesWithoutARequester() {
		EventNotification n = claimed(NotificationKind.INVITATION, null);

		worker.process(n.getId());

		verify(outbox).fanOutInvitation(n.getId(), event.getId(), group.getId(), type.getId(), "EVENT_INVITATION", null, NOW);
	}

	@Test
	void process_whenNoLongerBeingSent_skipsIt() {
		EventNotification n = claimed(NotificationKind.INVITATION, organizer);
		n.cancel();

		worker.process(n.getId());

		verify(outbox, never()).fanOutInvitation(any(), any(), any(), any(), anyString(), any(), any());
		assertThat(n.getStatus()).isEqualTo(NotificationStatus.CANCELLED);
	}

	@Test
	void process_whenTheNotificationIsGone_skipsIt() {
		worker.process(UUID.randomUUID());

		verifyNoInteractions(outbox);
	}

}

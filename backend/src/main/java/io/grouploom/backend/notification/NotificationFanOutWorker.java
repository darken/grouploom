package io.grouploom.backend.notification;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.email.EmailKind;
import io.grouploom.backend.email.EmailOutboxRepository;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;

/** Expands one claimed notification in a single transaction: the set-based insert and the mark commit together. */
@Component
public class NotificationFanOutWorker {

	private static final Logger log = LoggerFactory.getLogger(NotificationFanOutWorker.class);

	private final EventNotificationRepository notifications;
	private final EventRepository events;
	private final EmailOutboxRepository outbox;
	private final Clock clock;

	public NotificationFanOutWorker(EventNotificationRepository notifications, EventRepository events,
			EmailOutboxRepository outbox, Clock clock) {
		this.notifications = notifications;
		this.events = events;
		this.outbox = outbox;
		this.clock = clock;
	}

	@Transactional
	public void process(UUID id) {
		EventNotification notification = notifications.findById(id).orElse(null);
		if (notification == null || notification.getStatus() != NotificationStatus.SENDING) {
			log.info("Notification {} is gone or no longer being sent; skipping", id);
			return;
		}
		Event event = events.findByIdForShare(notification.getEvent().getId()).orElseThrow();
		NotificationKind kind = notification.getKind();
		if (event.isCancelled() && kind != NotificationKind.CANCELLATION) {
			notification.cancel();
			return;
		}
		Instant now = clock.instant();
		UUID requestedBy = notification.getCreatedBy() == null ? null : notification.getCreatedBy().getId();
		UUID groupId = notification.getGroup().getId();
		int count = switch (kind) {
			case INVITATION -> outbox.fanOutInvitation(id, event.getId(), groupId, event.getEventType().getId(),
					EmailKind.EVENT_INVITATION.name(), requestedBy, now);
			case REMINDER -> outbox.fanOutInvitation(id, event.getId(), groupId, event.getEventType().getId(),
					EmailKind.EVENT_REMINDER.name(), requestedBy, now);
			case CANCELLATION -> outbox.fanOutCancellation(id, event.getId(), groupId, requestedBy, now);
		};
		notification.markFannedOut(count, now);
		log.info("Notification {} queued {} emails", id, count);
	}

}

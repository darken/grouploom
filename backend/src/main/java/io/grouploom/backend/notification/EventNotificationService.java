package io.grouploom.backend.notification;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.email.DeliveryCount;
import io.grouploom.backend.email.EmailKind;
import io.grouploom.backend.email.EmailOutboxRepository;
import io.grouploom.backend.email.EmailStatus;
import io.grouploom.backend.error.BadRequestException;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.event.Event;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.notification.dto.AudienceResponse;
import io.grouploom.backend.notification.dto.DeliveryCounts;
import io.grouploom.backend.notification.dto.EventNotificationResponse;
import io.grouploom.backend.notification.dto.ScheduleNotificationRequest;
import io.grouploom.backend.user.repository.UserRepository;

@Service
public class EventNotificationService {

	static final int MAX_SCHEDULED = 10;
	/** Allowance for clock skew between client and server when checking the send time isn't in the past. */
	private static final Duration PAST_ALLOWANCE = Duration.ofMinutes(1);

	private final EventRepository events;
	private final EventNotificationRepository notifications;
	private final EmailOutboxRepository outbox;
	private final UserRepository users;
	private final GroupRepository groups;
	private final Clock clock;

	public EventNotificationService(EventRepository events, EventNotificationRepository notifications,
			EmailOutboxRepository outbox, UserRepository users, GroupRepository groups, Clock clock) {
		this.events = events;
		this.notifications = notifications;
		this.outbox = outbox;
		this.users = users;
		this.groups = groups;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public List<EventNotificationResponse> list(CurrentUser current, UUID eventId) {
		Event event = findEvent(current, eventId);
		List<EventNotification> all = notifications.findAllByEventIdOrderBySendAtAsc(event.getId());
		if (all.isEmpty()) {
			return List.of();
		}
		Map<UUID, Map<EmailStatus, Long>> counts = new HashMap<>();
		for (DeliveryCount c : outbox.countByNotification(all.stream().map(EventNotification::getId).toList())) {
			counts.computeIfAbsent(c.notificationId(), k -> new EnumMap<>(EmailStatus.class)).put(c.status(), c.count());
		}
		return all.stream().map(n -> toResponse(n, event.getZoneId(), delivery(counts.get(n.getId())))).toList();
	}

	@Transactional(readOnly = true)
	public AudienceResponse audience(CurrentUser current, UUID eventId, NotificationKind kind) {
		Event event = findEvent(current, eventId);
		EmailKind emailKind = switch (kind) {
			case INVITATION -> EmailKind.EVENT_INVITATION;
			case REMINDER -> EmailKind.EVENT_REMINDER;
			case CANCELLATION -> throw new BadRequestException("Only invitations and reminders have an audience preview.",
					"notification.invalid-kind", "kind");
		};
		return new AudienceResponse(
				outbox.countEligibleRecipients(current.groupId(), event.getEventType().getId(), event.getId(), emailKind.name()),
				outbox.countMembers(current.groupId()));
	}

	@Transactional
	public EventNotificationResponse schedule(CurrentUser current, UUID eventId, ScheduleNotificationRequest request) {
		Event event = findEvent(current, eventId);
		if (request.kind() == NotificationKind.CANCELLATION) {
			throw new BadRequestException("Only invitations and reminders can be scheduled.", "notification.invalid-kind",
					"kind");
		}
		if (event.isCancelled()) {
			throw new ConflictException("This event is cancelled.", "event.cancelled", null);
		}
		Instant now = clock.instant();
		if (!event.getStartsAt().isAfter(now)) {
			throw new ConflictException("This event has already started.", "event.started", null);
		}
		if (notifications.countByEventIdAndStatus(event.getId(), NotificationStatus.SCHEDULED) >= MAX_SCHEDULED) {
			throw new ConflictException("An event can have at most " + MAX_SCHEDULED + " scheduled notifications.",
					"notification.limit", null);
		}

		Instant sendAt;
		Integer offset = null;
		switch (request.timing()) {
			case NOW -> sendAt = now;
			case BEFORE_START -> {
				offset = request.offsetMinutes();
				sendAt = event.getStartsAt().minus(Duration.ofMinutes(offset));
				validateSendAt(sendAt, event, now, "offsetMinutes");
			}
			case AT -> {
				sendAt = ZonedDateTime.ofLocal(request.localSendAt(), event.getZoneId(), null).toInstant();
				validateSendAt(sendAt, event, now, "localSendAt");
			}
			default -> throw new IllegalStateException("Unknown timing " + request.timing());
		}

		EventNotification created = new EventNotification(event, groups.getReferenceById(current.groupId()),
				request.kind(), sendAt, offset, clean(request.message()), users.getReferenceById(current.userId()), now);
		return toResponse(notifications.save(created), event.getZoneId(), DeliveryCounts.NONE);
	}

	@Transactional
	public void unschedule(CurrentUser current, UUID eventId, UUID notificationId) {
		EventNotification notification = notifications
			.findByIdAndEventIdAndGroupId(notificationId, eventId, current.groupId())
			.orElseThrow(() -> new NotFoundException("Notification not found."));
		if (notification.getStatus() != NotificationStatus.SCHEDULED) {
			throw alreadySent();
		}
		try {
			notifications.delete(notification);
			notifications.flush();
		}
		catch (ObjectOptimisticLockingFailureException e) {
			// The fan-out claimed it between our read and the delete.
			throw alreadySent();
		}
	}

	/** Called by {@code EventService.update} in the same transaction when the start moves. */
	@Transactional
	public void onEventRescheduled(Event event, Instant newStart) {
		Instant now = clock.instant();
		for (EventNotification n : scheduled(event)) {
			if (n.getOffsetMinutes() != null) {
				n.reschedule(newStart, now);
			}
			else if (!n.getSendAt().isBefore(newStart)) {
				throw new BadRequestException(
						"A notification is scheduled for after the new start. Unschedule it first.",
						"event.notification-after-start", "localStart");
			}
		}
	}

	/** Called by {@code EventService.cancel} after the event has been flushed as cancelled. */
	@Transactional
	public void onEventCancelled(Event event, CurrentUser current) {
		scheduled(event).forEach(EventNotification::cancel);
		if (notifications.existsByEventIdAndStatus(event.getId(), NotificationStatus.SENT)) {
			Instant now = clock.instant();
			notifications.save(new EventNotification(event, event.getGroup(), NotificationKind.CANCELLATION, now, null,
					event.getCancellationMessage(), users.getReferenceById(current.userId()), now));
		}
	}

	private List<EventNotification> scheduled(Event event) {
		return notifications.findAllByEventIdOrderBySendAtAsc(event.getId())
			.stream()
			.filter(n -> n.getStatus() == NotificationStatus.SCHEDULED)
			.toList();
	}

	private static void validateSendAt(Instant sendAt, Event event, Instant now, String field) {
		if (sendAt.isBefore(now.minus(PAST_ALLOWANCE)) || !sendAt.isBefore(event.getStartsAt())) {
			throw new BadRequestException("The send time must be in the future and before the event starts.",
					"notification.send-at-invalid", field);
		}
	}

	private Event findEvent(CurrentUser current, UUID eventId) {
		// Scoped by group: another group's id is indistinguishable from a missing one.
		return events.findByIdAndGroupId(eventId, current.groupId())
			.orElseThrow(() -> new NotFoundException("Event not found."));
	}

	private static String clean(String message) {
		return message == null || message.isBlank() ? null : message.trim();
	}

	private static ConflictException alreadySent() {
		return new ConflictException("This notification has already been sent.", "notification.already-sent", null);
	}

	private static DeliveryCounts delivery(Map<EmailStatus, Long> counts) {
		if (counts == null) {
			return DeliveryCounts.NONE;
		}
		return new DeliveryCounts(counts.getOrDefault(EmailStatus.PENDING, 0L) + counts.getOrDefault(EmailStatus.SENDING, 0L),
				counts.getOrDefault(EmailStatus.SENT, 0L), counts.getOrDefault(EmailStatus.FAILED, 0L));
	}

	private static EventNotificationResponse toResponse(EventNotification n, ZoneId zone, DeliveryCounts delivery) {
		return new EventNotificationResponse(n.getId(), n.getKind(), n.getStatus(), n.getSendAt(),
				n.getSendAt().atZone(zone).toLocalDateTime(), zone.getId(), n.getOffsetMinutes(), n.getMessage(),
				n.getRecipientCount(), delivery, n.getCreatedAt(), n.getSentAt());
	}

}

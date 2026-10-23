package io.grouploom.backend.event;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.BadRequestException;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.event.dto.CancelEventRequest;
import io.grouploom.backend.event.dto.CreateEventRequest;
import io.grouploom.backend.event.dto.EventResponse;
import io.grouploom.backend.event.dto.UpdateEventRequest;
import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.eventtype.EventTypeRepository;
import io.grouploom.backend.group.GroupRepository;
import io.grouploom.backend.notification.EventNotificationRepository;
import io.grouploom.backend.notification.EventNotificationService;
import io.grouploom.backend.notification.NotificationStatus;

@Service
public class EventService {

	/** Allowance for clock skew between client and server when checking the start isn't in the past. */
	private static final Duration PAST_ALLOWANCE = Duration.ofMinutes(1);
	private static final Duration MAX_DURATION = Duration.ofDays(7);

	private final EventRepository events;
	private final EventTypeRepository eventTypes;
	private final GroupRepository groups;
	private final EventNotificationService notificationService;
	private final EventNotificationRepository notifications;
	private final Clock clock;

	public EventService(EventRepository events, EventTypeRepository eventTypes, GroupRepository groups,
			EventNotificationService notificationService, EventNotificationRepository notifications, Clock clock) {
		this.events = events;
		this.eventTypes = eventTypes;
		this.groups = groups;
		this.notificationService = notificationService;
		this.notifications = notifications;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<EventResponse> list(CurrentUser current, When when, UUID eventTypeId, Pageable pageable) {
		Instant now = clock.instant();
		Page<Event> page = when == When.PAST
				? events.findPast(current.groupId(), now, eventTypeId, pageable)
				: events.findUpcoming(current.groupId(), now, eventTypeId, pageable);
		return page.map(EventMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public EventResponse get(CurrentUser current, UUID id) {
		return EventMapper.toResponse(find(current, id));
	}

	@Transactional
	public EventResponse create(CurrentUser current, CreateEventRequest request) {
		EventType type = resolveNewType(current, request.eventTypeId());
		Times times = times(request.localStart(), request.localEnd(), request.timeZone());
		if (times.start().isBefore(clock.instant().minus(PAST_ALLOWANCE))) {
			throw new BadRequestException("The start can't be in the past.", "event.start-in-past", "localStart");
		}
		Event created = new Event(groups.getReferenceById(current.groupId()), type, request.title().trim(),
				request.description(), request.location(), times.start(), times.end(), request.timeZone(),
				clock.instant());
		return EventMapper.toResponse(flush(created));
	}

	@Transactional
	public EventResponse update(CurrentUser current, UUID id, UpdateEventRequest request) {
		Event event = find(current, id);
		requireNotCancelled(event);
		if (request.version() != event.getVersion()) {
			throw stale();
		}
		// An event keeps its type even if that type was archived since; only a switch is checked.
		EventType type = request.eventTypeId().equals(event.getEventType().getId())
				? event.getEventType()
				: resolveNewType(current, request.eventTypeId());
		Times times = times(request.localStart(), request.localEnd(), request.timeZone());
		// Past events can still be corrected; the past check only applies when the start moves.
		if (!times.start().equals(event.getStartsAt()) && times.start().isBefore(clock.instant().minus(PAST_ALLOWANCE))) {
			throw new BadRequestException("The start can't be in the past.", "event.start-in-past", "localStart");
		}
		boolean startMoved = !times.start().equals(event.getStartsAt());
		event.update(type, request.title().trim(), request.description(), request.location(), times.start(),
				times.end(), request.timeZone(), clock.instant());
		if (startMoved) {
			notificationService.onEventRescheduled(event, times.start());
		}
		return EventMapper.toResponse(flush(event));
	}

	@Transactional
	public EventResponse cancel(CurrentUser current, UUID id, CancelEventRequest request) {
		Event event = find(current, id);
		requireNotCancelled(event);
		Instant now = clock.instant();
		if (!event.getEndsAt().isAfter(now)) {
			throw new ConflictException("This event has already ended.", "event.ended", null);
		}
		String message = request == null || request.message() == null || request.message().isBlank() ? null
				: request.message().trim();
		event.cancel(message, now);
		// Flush first: the row lock taken here makes a concurrent fan-out finish before we look at what was sent.
		EventResponse response = EventMapper.toResponse(flush(event));
		notificationService.onEventCancelled(event, current);
		return response;
	}

	private static void requireNotCancelled(Event event) {
		if (event.isCancelled()) {
			throw new ConflictException("This event is cancelled.", "event.cancelled", null);
		}
	}

	@Transactional
	public void delete(CurrentUser current, UUID id) {
		Event event = find(current, id);
		if (notifications.existsByEventIdAndStatus(event.getId(), NotificationStatus.SENT)) {
			throw new ConflictException("Members were notified about this event. Cancel it instead.",
					"event.has-notifications", null);
		}
		events.delete(event);
	}

	private record Times(Instant start, Instant end) {
	}

	/** Converts wall-clock times in {@code timeZone}: a DST gap shifts forward, an overlap takes the earlier offset. */
	private static Times times(LocalDateTime localStart, LocalDateTime localEnd, String timeZone) {
		ZoneId zone = ZoneId.of(timeZone);
		Instant start = ZonedDateTime.ofLocal(localStart, zone, null).toInstant();
		Instant end = ZonedDateTime.ofLocal(localEnd, zone, null).toInstant();
		if (!end.isAfter(start)) {
			throw new BadRequestException("The end must be after the start.", "event.end-before-start", "localEnd");
		}
		if (Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
			throw new BadRequestException("An event can last at most 7 days.", "event.too-long", "localEnd");
		}
		return new Times(start, end);
	}

	private EventType resolveNewType(CurrentUser current, UUID eventTypeId) {
		EventType type = eventTypes.findByIdAndGroupId(eventTypeId, current.groupId())
			.orElseThrow(EventService::typeNotFound);
		if (type.isArchived()) {
			throw new BadRequestException("This event type is archived. Choose another one.",
					"event.event-type-archived", "eventTypeId");
		}
		return type;
	}

	private Event find(CurrentUser current, UUID id) {
		// Scoped by group: another group's id is indistinguishable from a missing one.
		return events.findByIdAndGroupId(id, current.groupId()).orElseThrow(() -> new NotFoundException("Event not found."));
	}

	private Event flush(Event event) {
		try {
			return events.saveAndFlush(event);
		}
		catch (DataIntegrityViolationException e) {
			// The only constraint the checks above can miss is the type being deleted in between.
			throw typeNotFound();
		}
		catch (ObjectOptimisticLockingFailureException e) {
			throw stale();
		}
	}

	private static BadRequestException typeNotFound() {
		return new BadRequestException("Event type not found.", "event.event-type-not-found", "eventTypeId");
	}

	private static ConflictException stale() {
		return new ConflictException("This event was changed by someone else. Reload and try again.", "event.stale", null);
	}

}

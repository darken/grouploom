package io.grouploom.backend.eventtype;

import java.time.Clock;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.grouploom.backend.auth.CurrentUser;
import io.grouploom.backend.error.ConflictException;
import io.grouploom.backend.error.NotFoundException;
import io.grouploom.backend.event.EventRepository;
import io.grouploom.backend.eventtype.dto.CreateEventTypeRequest;
import io.grouploom.backend.eventtype.dto.EventTypeResponse;
import io.grouploom.backend.eventtype.dto.UpdateEventTypeRequest;
import io.grouploom.backend.group.GroupRepository;

@Service
public class EventTypeService {

	private final EventTypeRepository eventTypes;
	private final GroupRepository groups;
	private final EventRepository events;
	private final Clock clock;

	public EventTypeService(EventTypeRepository eventTypes, GroupRepository groups, EventRepository events,
			Clock clock) {
		this.eventTypes = eventTypes;
		this.groups = groups;
		this.events = events;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Page<EventTypeResponse> list(CurrentUser current, boolean archived, Pageable pageable) {
		Page<EventType> page = archived
				? eventTypes.findAllByGroupIdAndArchivedAtIsNotNull(current.groupId(), pageable)
				: eventTypes.findAllByGroupIdAndArchivedAtIsNull(current.groupId(), pageable);
		return page.map(EventTypeMapper::toResponse);
	}

	@Transactional(readOnly = true)
	public EventTypeResponse get(CurrentUser current, UUID id) {
		return EventTypeMapper.toResponse(find(current, id));
	}

	@Transactional
	public EventTypeResponse create(CurrentUser current, CreateEventTypeRequest request) {
		String name = request.name().trim();
		if (eventTypes.existsByGroupIdAndNameIgnoreCase(current.groupId(), name)) {
			throw nameTaken();
		}
		EventType created = new EventType(groups.getReferenceById(current.groupId()), name, request.description(),
				request.color(), request.defaultDurationMinutes(), clock.instant());
		return EventTypeMapper.toResponse(flush(created));
	}

	@Transactional
	public EventTypeResponse update(CurrentUser current, UUID id, UpdateEventTypeRequest request) {
		EventType eventType = find(current, id);
		if (request.version() != eventType.getVersion()) {
			throw stale();
		}
		String name = request.name().trim();
		if (eventTypes.existsByGroupIdAndNameIgnoreCaseAndIdNot(current.groupId(), name, id)) {
			throw nameTaken();
		}
		eventType.update(name, request.description(), request.color(), request.defaultDurationMinutes(),
				clock.instant());
		if (request.archived()) {
			eventType.archive(clock.instant());
		}
		else {
			eventType.unarchive();
		}
		return EventTypeMapper.toResponse(flush(eventType));
	}

	@Transactional
	public void delete(CurrentUser current, UUID id) {
		EventType eventType = find(current, id);
		if (events.existsByEventTypeId(id)) {
			throw inUse();
		}
		eventTypes.delete(eventType);
		try {
			eventTypes.flush();
		}
		catch (DataIntegrityViolationException e) {
			// An event was created between the check and the delete; the foreign key decided.
			throw inUse();
		}
	}

	private EventType find(CurrentUser current, UUID id) {
		// Scoped by group: another group's id is indistinguishable from a missing one.
		return eventTypes.findByIdAndGroupId(id, current.groupId())
			.orElseThrow(() -> new NotFoundException("Event type not found."));
	}

	private EventType flush(EventType eventType) {
		try {
			return eventTypes.saveAndFlush(eventType);
		}
		catch (DataIntegrityViolationException e) {
			// Two requests raced past the existence check; the unique index decided.
			throw nameTaken();
		}
		catch (ObjectOptimisticLockingFailureException e) {
			throw stale();
		}
	}

	private static ConflictException nameTaken() {
		return new ConflictException("An event type with this name already exists.", "event-type.name-taken", "name");
	}

	private static ConflictException inUse() {
		return new ConflictException("This event type is used by events. Archive it instead.", "event-type.in-use", null);
	}

	private static ConflictException stale() {
		return new ConflictException("This event type was changed by someone else. Reload and try again.",
				"event-type.stale", null);
	}

}

package io.grouploom.backend.event;

import io.grouploom.backend.event.dto.EventResponse;
import io.grouploom.backend.event.dto.EventTypeSummary;
import io.grouploom.backend.eventtype.EventType;

public final class EventMapper {

	private EventMapper() {
	}

	public static EventResponse toResponse(Event e) {
		EventType type = e.getEventType();
		return new EventResponse(e.getId(), e.getTitle(), e.getDescription(), e.getLocation(), e.getStartsAt(),
				e.getEndsAt(), e.getStartsAt().atZone(e.getZoneId()).toLocalDateTime(),
				e.getEndsAt().atZone(e.getZoneId()).toLocalDateTime(), e.getTimeZone(),
				new EventTypeSummary(type.getId(), type.getName(), type.getColor(), type.isArchived()), e.getVersion(),
				e.getCreatedAt(), e.getUpdatedAt());
	}

}

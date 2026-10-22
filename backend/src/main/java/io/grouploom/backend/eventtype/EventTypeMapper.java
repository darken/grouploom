package io.grouploom.backend.eventtype;

import io.grouploom.backend.eventtype.dto.EventTypeResponse;

public final class EventTypeMapper {

	private EventTypeMapper() {
	}

	public static EventTypeResponse toResponse(EventType e) {
		return new EventTypeResponse(e.getId(), e.getName(), e.getDescription(), e.getColor(),
				e.getDefaultDurationMinutes(), e.isArchived(), e.getVersion(), e.getCreatedAt(), e.getUpdatedAt());
	}

}

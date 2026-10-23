package io.grouploom.backend.event.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

// Absent optionals are omitted, matching the generated TypeScript types (`field?: T`, never null).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String title,
		String description,
		String location,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The start as an instant (UTC offset included).") Instant start,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The end as an instant (UTC offset included).") Instant end,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "date-time", example = "2026-11-02T10:00:00",
				description = "The start as wall-clock time in timeZone, without an offset.") LocalDateTime localStart,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "date-time", example = "2026-11-02T12:00:00",
				description = "The end as wall-clock time in timeZone, without an offset.") LocalDateTime localEnd,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Europe/Madrid") String timeZone,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventTypeSummary eventType,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
		@Schema(description = "Set once the event is cancelled.") Instant cancelledAt,
		String cancellationMessage) {
}

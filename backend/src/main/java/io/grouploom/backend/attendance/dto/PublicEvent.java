package io.grouploom.backend.attendance.dto;

import java.time.Instant;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.grouploom.backend.eventtype.EventTypeColor;
import io.swagger.v3.oas.annotations.media.Schema;

/** The event as a member sees it on the reply page. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicEvent(
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
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String typeName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventTypeColor typeColor,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean cancelled,
		String cancellationMessage) {
}

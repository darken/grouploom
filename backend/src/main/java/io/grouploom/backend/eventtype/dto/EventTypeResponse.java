package io.grouploom.backend.eventtype.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.grouploom.backend.eventtype.EventTypeColor;
import io.swagger.v3.oas.annotations.media.Schema;

// Absent optionals are omitted, matching the generated TypeScript types (`field?: T`, never null).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventTypeResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		String description,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventTypeColor color,
		Integer defaultDurationMinutes,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean archived,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt) {
}

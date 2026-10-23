package io.grouploom.backend.event.dto;

import java.util.UUID;

import io.grouploom.backend.eventtype.EventTypeColor;
import io.swagger.v3.oas.annotations.media.Schema;

public record EventTypeSummary(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventTypeColor color,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean archived) {
}

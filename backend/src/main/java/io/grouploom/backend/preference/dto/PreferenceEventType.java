package io.grouploom.backend.preference.dto;

import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.grouploom.backend.eventtype.EventTypeColor;
import io.swagger.v3.oas.annotations.media.Schema;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PreferenceEventType(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		String description,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) EventTypeColor color,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean subscribed) {
}

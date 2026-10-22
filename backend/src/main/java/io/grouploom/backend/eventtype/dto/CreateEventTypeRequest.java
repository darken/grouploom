package io.grouploom.backend.eventtype.dto;

import io.grouploom.backend.eventtype.EventTypeColor;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateEventTypeRequest(
		@NotBlank @Size(max = 80) String name,
		@Size(max = 500) String description,
		@NotNull EventTypeColor color,
		@Min(5) @Max(1440) Integer defaultDurationMinutes) {
}

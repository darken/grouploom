package io.grouploom.backend.event.dto;

import java.time.LocalDateTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidTimeRange
public record UpdateEventRequest(
		@NotNull UUID eventTypeId,
		@NotBlank @Size(max = 120) String title,
		@Size(max = 2000) String description,
		@Size(max = 200) String location,
		@NotNull @Schema(type = "string", format = "date-time", example = "2026-11-02T10:00:00",
				description = "Wall-clock time in timeZone, without an offset.") LocalDateTime localStart,
		@NotNull @Schema(type = "string", format = "date-time", example = "2026-11-02T12:00:00",
				description = "Wall-clock time in timeZone, without an offset.") LocalDateTime localEnd,
		@NotBlank @ValidTimeZone @Schema(example = "Europe/Madrid") String timeZone,
		@NotNull Long version) implements HasLocalTimes {

	public UpdateEventRequest {
		title = title == null ? null : title.trim();
	}

}

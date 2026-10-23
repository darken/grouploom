package io.grouploom.backend.event.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

public record CancelEventRequest(
		@Size(max = 1000) @Schema(description = "Optional note shown on the event and in the cancellation email.") String message) {
}

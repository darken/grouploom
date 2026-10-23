package io.grouploom.backend.notification.dto;

import java.time.LocalDateTime;

import io.grouploom.backend.notification.NotificationKind;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@ValidTiming
public record ScheduleNotificationRequest(
		@NotNull @Schema(allowableValues = { "INVITATION", "REMINDER" }, description = "CANCELLATION is created by the system.") NotificationKind kind,
		@NotNull NotificationTiming timing,
		@Min(15) @Max(43200) @Schema(description = "Minutes before the start; required for BEFORE_START (15 to 43200).") Integer offsetMinutes,
		@Schema(type = "string", format = "date-time", example = "2026-11-01T10:00:00",
				description = "Wall-clock time in the event's time zone, without an offset; required for AT.") LocalDateTime localSendAt,
		@Size(max = 1000) @Schema(description = "Optional note from the organizer, shown in the email.") String message) {
}

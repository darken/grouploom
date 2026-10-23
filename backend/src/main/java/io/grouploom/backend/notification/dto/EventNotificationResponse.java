package io.grouploom.backend.notification.dto;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.grouploom.backend.notification.NotificationKind;
import io.grouploom.backend.notification.NotificationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

// Absent optionals are omitted, matching the generated TypeScript types (`field?: T`, never null).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventNotificationResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) NotificationKind kind,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) NotificationStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant sendAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, type = "string", format = "date-time", example = "2026-11-01T10:00:00",
				description = "sendAt as wall-clock time in timeZone, without an offset.") LocalDateTime localSendAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, example = "Europe/Madrid", description = "The event's time zone.") String timeZone,
		@Schema(description = "Set for notifications sent a number of minutes before the start; they move with the event.") Integer offsetMinutes,
		String message,
		@Schema(description = "How many members the notification was sent to; set once it has been sent.") Integer recipientCount,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) DeliveryCounts delivery,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
		Instant sentAt) {
}

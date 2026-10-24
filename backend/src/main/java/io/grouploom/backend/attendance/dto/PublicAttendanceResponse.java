package io.grouploom.backend.attendance.dto;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.grouploom.backend.attendance.AttendanceResponse;
import io.swagger.v3.oas.annotations.media.Schema;

// Absent optionals are omitted, matching the generated TypeScript types (`field?: T`, never null).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PublicAttendanceResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String groupName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String memberName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) PublicEvent event,
		@Schema(description = "The member's current answer; absent until they reply.") AttendanceResponse response,
		String note,
		Instant respondedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED,
				description = "Whether the member can still answer: the event is neither cancelled nor started.") boolean open) {
}

package io.grouploom.backend.attendance.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AttendanceSummaryResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long going,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long notGoing,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Current members without an answer.") long noReply,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Current members in the group.") long members) {
}

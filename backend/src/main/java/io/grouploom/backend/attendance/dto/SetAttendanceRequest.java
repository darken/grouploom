package io.grouploom.backend.attendance.dto;

import io.grouploom.backend.attendance.AttendanceResponse;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SetAttendanceRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull AttendanceResponse response,
		@Schema(description = "Plain text, at most 280 characters.") @Size(max = 280) String note) {
}

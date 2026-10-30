package io.grouploom.backend.attendance.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonInclude;

import io.grouploom.backend.attendance.AttendanceResponse;
import io.grouploom.backend.attendance.AttendanceSource;
import io.swagger.v3.oas.annotations.media.Schema;

// Absent optionals are omitted, matching the generated TypeScript types (`field?: T`, never null).
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttendanceRowResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) MemberSummary member,
		@Schema(description = "Absent when the member hasn't replied.") AttendanceResponse response,
		String note,
		@Schema(description = "Who set the latest answer. Absent when there is none.") AttendanceSource source,
		Instant respondedAt) {

	public record MemberSummary(
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email) {
	}

}

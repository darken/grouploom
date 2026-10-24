package io.grouploom.backend.attendance;

import java.time.Instant;
import java.util.UUID;

/** One current member with their answer for an event; the answer fields are null when there is none. */
public record AttendanceRow(UUID memberId, String memberName, String memberEmail, AttendanceResponse response,
		String note, AttendanceSource source, Instant respondedAt) {
}

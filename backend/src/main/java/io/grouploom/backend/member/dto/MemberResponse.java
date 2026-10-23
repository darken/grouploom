package io.grouploom.backend.member.dto;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record MemberResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY,
				description = "The member paused all invitations. Only the member can change this.") boolean invitationsPaused) {
}

package io.grouploom.backend.member.dto;

import java.time.Instant;
import java.util.UUID;

import io.grouploom.backend.email.dto.EmailDeliveryStatus;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long version,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant updatedAt,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, accessMode = Schema.AccessMode.READ_ONLY,
				description = "The member paused all invitations. Only the member can change this.") boolean invitationsPaused,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, nullable = true,
				description = "Latest preferences-link email to this member; null if none was ever requested.") EmailDeliveryStatus preferencesEmail) {
}

package io.grouploom.backend.preference.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

public record PublicPreferencesResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String memberName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Masked address, e.g. a••@example.com") String maskedEmail,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String groupName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean invitationsPaused,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) List<PreferenceEventType> eventTypes) {
}

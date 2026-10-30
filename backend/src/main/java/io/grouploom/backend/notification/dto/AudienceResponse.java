package io.grouploom.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AudienceResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Members a notification of the requested kind would reach right now.") int eligible,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Members in the group.") long total) {
}

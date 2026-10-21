package io.grouploom.backend.auth.dto;

import io.grouploom.backend.user.dto.MeResponse;
import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String accessToken,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Access token lifetime in seconds") long expiresIn,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) MeResponse user) {
}

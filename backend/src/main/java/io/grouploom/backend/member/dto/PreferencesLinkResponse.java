package io.grouploom.backend.member.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record PreferencesLinkResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Personal link; the token is in the URL fragment") String url,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {
}

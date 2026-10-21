package io.grouploom.backend.user.dto;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

public record GroupSummary(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name) {
}

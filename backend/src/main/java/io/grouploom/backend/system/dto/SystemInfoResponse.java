package io.grouploom.backend.system.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record SystemInfoResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String version) {
}

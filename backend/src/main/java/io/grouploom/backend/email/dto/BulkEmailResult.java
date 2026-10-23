package io.grouploom.backend.email.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** {@code skipped} members were emailed too recently (cooldown) and got nothing new. */
public record BulkEmailResult(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int queued,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) int skipped) {
}

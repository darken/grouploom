package io.grouploom.backend.group.dto;

import io.grouploom.backend.group.Language;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record UpdateGroupRequest(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) @NotNull Language language) {
}

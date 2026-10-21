package io.grouploom.backend.user.dto;

import java.util.UUID;

import io.grouploom.backend.user.Role;
import io.grouploom.backend.user.User;
import io.swagger.v3.oas.annotations.media.Schema;

public record MeResponse(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String displayName,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Role role,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) GroupSummary group) {

	public static MeResponse from(User user) {
		return new MeResponse(user.getId(), user.getEmail(), user.getDisplayName(), user.getRole(),
				new GroupSummary(user.getGroup().getId(), user.getGroup().getName()));
	}

}

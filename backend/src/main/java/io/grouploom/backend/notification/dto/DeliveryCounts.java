package io.grouploom.backend.notification.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record DeliveryCounts(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Queued or being sent.") long pending,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long sent,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) long failed) {

	public static final DeliveryCounts NONE = new DeliveryCounts(0, 0, 0);

}

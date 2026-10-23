package io.grouploom.backend.email.dto;

import java.time.Instant;

import io.grouploom.backend.email.EmailStatus;
import io.swagger.v3.oas.annotations.media.Schema;

/** Where the latest email to a member stands; {@code at} is when it was sent, or queued if not sent yet. */
public record EmailDeliveryStatus(
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) EmailStatus status,
		@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant at) {
}

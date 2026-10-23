package io.grouploom.backend.email.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

public record EmailQueuedResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant queuedAt) {
}

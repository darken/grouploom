package io.grouploom.backend.preference.dto;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

public record EventTypeSubscription(@NotNull UUID id, @NotNull Boolean subscribed) {
}

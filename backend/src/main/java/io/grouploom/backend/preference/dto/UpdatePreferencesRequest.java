package io.grouploom.backend.preference.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdatePreferencesRequest(
		@NotNull Boolean invitationsPaused,
		@NotNull @Size(max = 200) List<@Valid @NotNull EventTypeSubscription> eventTypes) {
}

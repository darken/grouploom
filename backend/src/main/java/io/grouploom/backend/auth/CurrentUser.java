package io.grouploom.backend.auth;

import java.util.UUID;

import io.grouploom.backend.user.Role;

/** The authenticated caller. Services scope every query by {@code groupId}. */
public record CurrentUser(UUID userId, UUID groupId, Role role) {
}

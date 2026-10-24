package io.grouploom.backend.attendance;

import java.util.UUID;

public record GoingCount(UUID eventId, long count) {
}

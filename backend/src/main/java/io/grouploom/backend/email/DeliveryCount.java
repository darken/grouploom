package io.grouploom.backend.email;

import java.util.UUID;

/** How many outbox rows a notification has in one status. */
public record DeliveryCount(UUID notificationId, EmailStatus status, long count) {
}

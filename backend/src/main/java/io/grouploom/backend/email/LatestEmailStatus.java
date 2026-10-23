package io.grouploom.backend.email;

import java.time.Instant;
import java.util.UUID;

/** Projection of the newest outbox entry of a member. */
public interface LatestEmailStatus {

	UUID getMemberId();

	String getStatus();

	Instant getSentAt();

	Instant getCreatedAt();

}

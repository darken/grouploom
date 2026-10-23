package io.grouploom.backend.preference;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "member_event_type_opt_outs")
public class EventTypeOptOut {

	@EmbeddedId
	private EventTypeOptOutId id;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EventTypeOptOut() {
	}

	public EventTypeOptOut(UUID memberId, UUID eventTypeId, Instant createdAt) {
		this.id = new EventTypeOptOutId(memberId, eventTypeId);
		this.createdAt = createdAt;
	}

	public EventTypeOptOutId getId() {
		return id;
	}

	public UUID getMemberId() {
		return id.memberId();
	}

	public UUID getEventTypeId() {
		return id.eventTypeId();
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

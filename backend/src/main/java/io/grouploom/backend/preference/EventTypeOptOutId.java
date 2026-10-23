package io.grouploom.backend.preference;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public record EventTypeOptOutId(
		@Column(name = "member_id") UUID memberId,
		@Column(name = "event_type_id") UUID eventTypeId) {
}

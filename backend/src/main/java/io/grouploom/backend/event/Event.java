package io.grouploom.backend.event;

import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import io.grouploom.backend.eventtype.EventType;
import io.grouploom.backend.group.Group;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "events")
public class Event {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private Group group;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_type_id", nullable = false)
	private EventType eventType;

	@Column(nullable = false, length = 120)
	private String title;

	@Column(length = 2000)
	private String description;

	@Column(length = 200)
	private String location;

	@Column(name = "starts_at", nullable = false)
	private Instant startsAt;

	@Column(name = "ends_at", nullable = false)
	private Instant endsAt;

	@Column(name = "time_zone", nullable = false, length = 64)
	private String timeZone;

	@Version
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Event() {
	}

	public Event(Group group, EventType eventType, String title, String description, String location, Instant startsAt,
			Instant endsAt, String timeZone, Instant now) {
		this.group = group;
		this.eventType = eventType;
		this.title = title;
		this.description = description;
		this.location = location;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.timeZone = timeZone;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(EventType eventType, String title, String description, String location, Instant startsAt,
			Instant endsAt, String timeZone, Instant now) {
		this.eventType = eventType;
		this.title = title;
		this.description = description;
		this.location = location;
		this.startsAt = startsAt;
		this.endsAt = endsAt;
		this.timeZone = timeZone;
		this.updatedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public Group getGroup() {
		return group;
	}

	public EventType getEventType() {
		return eventType;
	}

	public String getTitle() {
		return title;
	}

	public String getDescription() {
		return description;
	}

	public String getLocation() {
		return location;
	}

	public Instant getStartsAt() {
		return startsAt;
	}

	public Instant getEndsAt() {
		return endsAt;
	}

	public String getTimeZone() {
		return timeZone;
	}

	public ZoneId getZoneId() {
		return ZoneId.of(timeZone);
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

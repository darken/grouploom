package io.grouploom.backend.eventtype;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import io.grouploom.backend.group.Group;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "event_types")
public class EventType {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private Group group;

	@Column(nullable = false, length = 80)
	private String name;

	@Column(length = 500)
	private String description;

	@Convert(converter = EventTypeColorConverter.class)
	@Column(nullable = false, length = 20)
	private EventTypeColor color;

	@Column(name = "default_duration_minutes")
	private Integer defaultDurationMinutes;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Version
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected EventType() {
	}

	public EventType(Group group, String name, String description, EventTypeColor color,
			Integer defaultDurationMinutes, Instant now) {
		this.group = group;
		this.name = name;
		this.description = description;
		this.color = color;
		this.defaultDurationMinutes = defaultDurationMinutes;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void rename(String name) {
		this.name = name;
	}

	public void update(String name, String description, EventTypeColor color, Integer defaultDurationMinutes,
			Instant now) {
		rename(name);
		this.description = description;
		this.color = color;
		this.defaultDurationMinutes = defaultDurationMinutes;
		this.updatedAt = now;
	}

	public void archive(Instant now) {
		if (archivedAt == null) {
			this.archivedAt = now;
		}
	}

	public void unarchive() {
		this.archivedAt = null;
	}

	public boolean isArchived() {
		return archivedAt != null;
	}

	public UUID getId() {
		return id;
	}

	public Group getGroup() {
		return group;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	public EventTypeColor getColor() {
		return color;
	}

	public Integer getDefaultDurationMinutes() {
		return defaultDurationMinutes;
	}

	public Instant getArchivedAt() {
		return archivedAt;
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

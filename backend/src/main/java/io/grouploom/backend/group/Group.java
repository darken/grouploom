package io.grouploom.backend.group;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "groups")
public class Group {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 10)
	private Language language;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Group() {
	}

	public Group(String name, Language language, Instant createdAt) {
		this.name = name;
		this.language = language;
		this.createdAt = createdAt;
	}

	public UUID getId() {
		return id;
	}

	public String getName() {
		return name;
	}

	public Language getLanguage() {
		return language;
	}

	public void changeLanguage(Language language) {
		this.language = language;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

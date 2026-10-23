package io.grouploom.backend.member;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

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
@Table(name = "members")
public class Member {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private Group group;

	@Column(nullable = false, length = 120)
	private String name;

	@Column(nullable = false, length = 254)
	private String email;

	@Version
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Member() {
	}

	public Member(Group group, String name, String email, Instant now) {
		this.group = group;
		this.name = name;
		this.email = email;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public void update(String name, String email, Instant now) {
		this.name = name;
		this.email = email;
		this.updatedAt = now;
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

	public String getEmail() {
		return email;
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

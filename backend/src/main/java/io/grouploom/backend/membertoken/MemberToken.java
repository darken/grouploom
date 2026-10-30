package io.grouploom.backend.membertoken;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import io.grouploom.backend.event.Event;
import io.grouploom.backend.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "member_tokens")
public class MemberToken {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false, updatable = false)
	private Member member;

	/** Set for {@link MemberTokenPurpose#ATTENDANCE} tokens only. */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "event_id", updatable = false)
	private Event event;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private MemberTokenPurpose purpose;

	@JdbcTypeCode(SqlTypes.CHAR)
	@Column(name = "token_hash", nullable = false, length = 64, unique = true)
	private String tokenHash;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "revoked_at")
	private Instant revokedAt;

	@Column(name = "last_used_at")
	private Instant lastUsedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected MemberToken() {
	}

	public MemberToken(Member member, MemberTokenPurpose purpose, String tokenHash, Instant expiresAt,
			Instant createdAt) {
		this.member = member;
		this.purpose = purpose;
		this.tokenHash = tokenHash;
		this.expiresAt = expiresAt;
		this.createdAt = createdAt;
	}

	public MemberToken(Member member, MemberTokenPurpose purpose, String tokenHash, Instant expiresAt,
			Instant createdAt, Event event) {
		this(member, purpose, tokenHash, expiresAt, createdAt);
		this.event = event;
	}

	public boolean isRevoked() {
		return revokedAt != null;
	}

	public boolean isExpired(Instant now) {
		return !expiresAt.isAfter(now);
	}

	public boolean isUsable(Instant now) {
		return !isRevoked() && !isExpired(now);
	}

	public void revoke(Instant now) {
		if (revokedAt == null) {
			this.revokedAt = now;
		}
	}

	public void markUsed(Instant now) {
		this.lastUsedAt = now;
	}

	public UUID getId() {
		return id;
	}

	public Member getMember() {
		return member;
	}

	public Event getEvent() {
		return event;
	}

	public MemberTokenPurpose getPurpose() {
		return purpose;
	}

	public String getTokenHash() {
		return tokenHash;
	}

	public Instant getExpiresAt() {
		return expiresAt;
	}

	public Instant getRevokedAt() {
		return revokedAt;
	}

	public Instant getLastUsedAt() {
		return lastUsedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

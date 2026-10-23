package io.grouploom.backend.preference;

import java.time.Instant;
import java.util.UUID;

import io.grouploom.backend.member.Member;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * Deliberately not versioned: members edit their own preferences last-write-wins, and doing so
 * must not touch {@code Member.version} (which would 409 an organizer editing the same member).
 */
@Entity
@Table(name = "member_preferences")
public class MemberPreferences {

	@Id
	@Column(name = "member_id")
	private UUID memberId;

	@MapsId
	@OneToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id")
	private Member member;

	@Column(name = "invitations_paused_at")
	private Instant invitationsPausedAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected MemberPreferences() {
	}

	public MemberPreferences(Member member, Instant now) {
		this.member = member;
		this.updatedAt = now;
	}

	/** Pausing an already paused member keeps the original pause time. */
	public void setInvitationsPaused(boolean paused, Instant now) {
		if (paused && invitationsPausedAt == null) {
			this.invitationsPausedAt = now;
		}
		else if (!paused) {
			this.invitationsPausedAt = null;
		}
		this.updatedAt = now;
	}

	public boolean isInvitationsPaused() {
		return invitationsPausedAt != null;
	}

	public UUID getMemberId() {
		return memberId;
	}

	public Member getMember() {
		return member;
	}

	public Instant getInvitationsPausedAt() {
		return invitationsPausedAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}

}

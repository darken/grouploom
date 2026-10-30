package io.grouploom.backend.attendance;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import io.grouploom.backend.event.Event;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A member's answer for an event. Written through {@link AttendanceRepository#upsert}, read as an entity. */
@Entity
@Table(name = "attendances")
public class Attendance {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private Group group;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false, updatable = false)
	private Event event;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false, updatable = false)
	private Member member;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AttendanceResponse response;

	@Column(length = 280)
	private String note;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private AttendanceSource source;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "set_by_user_id")
	private User setBy;

	@Column(name = "responded_at", nullable = false)
	private Instant respondedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Attendance() {
	}

	public UUID getId() {
		return id;
	}

	public Group getGroup() {
		return group;
	}

	public Event getEvent() {
		return event;
	}

	public Member getMember() {
		return member;
	}

	public AttendanceResponse getResponse() {
		return response;
	}

	public String getNote() {
		return note;
	}

	public AttendanceSource getSource() {
		return source;
	}

	public User getSetBy() {
		return setBy;
	}

	public Instant getRespondedAt() {
		return respondedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

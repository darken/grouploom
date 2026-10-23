package io.grouploom.backend.email;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import io.grouploom.backend.event.Event;
import io.grouploom.backend.group.Group;
import io.grouploom.backend.member.Member;
import io.grouploom.backend.notification.EventNotification;
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

/**
 * One email waiting to be sent. Holds no content and no token: the dispatcher
 * issues a fresh member token and renders the message at send time.
 */
@Entity
@Table(name = "email_outbox")
public class EmailOutboxEntry {

	static final int MAX_ERROR_LENGTH = 500;
	private static final Duration BASE_BACKOFF = Duration.ofMinutes(1);

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private Group group;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "member_id", nullable = false, updatable = false)
	private Member member;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "requested_by_user_id", updatable = false)
	private User requestedBy;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "event_id", updatable = false)
	private Event event;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "notification_id", updatable = false)
	private EventNotification notification;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 40, updatable = false)
	private EmailKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private EmailStatus status;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "next_attempt_at", nullable = false)
	private Instant nextAttemptAt;

	@Column(name = "claimed_until")
	private Instant claimedUntil;

	@Column(name = "last_error", length = MAX_ERROR_LENGTH)
	private String lastError;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EmailOutboxEntry() {
	}

	public EmailOutboxEntry(Group group, Member member, User requestedBy, EmailKind kind, Instant now) {
		this.group = group;
		this.member = member;
		this.requestedBy = requestedBy;
		this.kind = kind;
		this.status = EmailStatus.PENDING;
		this.nextAttemptAt = now;
		this.createdAt = now;
	}

	public EmailOutboxEntry(Group group, Member member, User requestedBy, EmailKind kind, Event event,
			EventNotification notification, Instant now) {
		this(group, member, requestedBy, kind, now);
		this.event = event;
		this.notification = notification;
	}

	public void claim(Instant now, Duration timeout) {
		this.status = EmailStatus.SENDING;
		this.claimedUntil = now.plus(timeout);
	}

	public void markSent(Instant now) {
		this.status = EmailStatus.SENT;
		this.sentAt = now;
		this.attempts++;
		this.claimedUntil = null;
		this.lastError = null;
	}

	/** Backs off 1, 2, 4, 8 … minutes, or fails for good once {@code maxAttempts} are used. */
	public void scheduleRetry(Instant now, String error, int maxAttempts) {
		this.attempts++;
		this.claimedUntil = null;
		this.lastError = truncate(error);
		if (attempts >= maxAttempts) {
			this.status = EmailStatus.FAILED;
			return;
		}
		this.status = EmailStatus.PENDING;
		this.nextAttemptAt = now.plus(BASE_BACKOFF.multipliedBy(1L << (attempts - 1)));
	}

	public void fail(Instant now, String error) {
		this.status = EmailStatus.FAILED;
		this.attempts++;
		this.claimedUntil = null;
		this.lastError = truncate(error);
	}

	private static String truncate(String error) {
		if (error == null || error.length() <= MAX_ERROR_LENGTH) {
			return error;
		}
		return error.substring(0, MAX_ERROR_LENGTH);
	}

	public UUID getId() {
		return id;
	}

	public Group getGroup() {
		return group;
	}

	public Member getMember() {
		return member;
	}

	public User getRequestedBy() {
		return requestedBy;
	}

	public Event getEvent() {
		return event;
	}

	public EventNotification getNotification() {
		return notification;
	}

	public EmailKind getKind() {
		return kind;
	}

	public EmailStatus getStatus() {
		return status;
	}

	public int getAttempts() {
		return attempts;
	}

	public Instant getNextAttemptAt() {
		return nextAttemptAt;
	}

	public Instant getClaimedUntil() {
		return claimedUntil;
	}

	public String getLastError() {
		return lastError;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

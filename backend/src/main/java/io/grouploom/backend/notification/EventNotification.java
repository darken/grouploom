package io.grouploom.backend.notification;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.UuidGenerator;

import io.grouploom.backend.event.Event;
import io.grouploom.backend.group.Group;
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
import jakarta.persistence.Version;

/**
 * A notification waiting to be expanded into one email per recipient. Recipients are chosen when it is
 * sent, not when it is scheduled.
 */
@Entity
@Table(name = "event_notifications")
public class EventNotification {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "event_id", nullable = false, updatable = false)
	private Event event;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "group_id", nullable = false, updatable = false)
	private Group group;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20, updatable = false)
	private NotificationKind kind;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private NotificationStatus status;

	@Column(name = "send_at", nullable = false)
	private Instant sendAt;

	@Column(name = "offset_minutes", updatable = false)
	private Integer offsetMinutes;

	@Column(length = 1000, updatable = false)
	private String message;

	@Column(name = "recipient_count")
	private Integer recipientCount;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by_user_id", updatable = false)
	private User createdBy;

	@Column(name = "claimed_until")
	private Instant claimedUntil;

	@Column(name = "sent_at")
	private Instant sentAt;

	@Version
	private long version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected EventNotification() {
	}

	public EventNotification(Event event, Group group, NotificationKind kind, Instant sendAt, Integer offsetMinutes,
			String message, User createdBy, Instant now) {
		this.event = event;
		this.group = group;
		this.kind = kind;
		this.status = NotificationStatus.SCHEDULED;
		this.sendAt = sendAt;
		this.offsetMinutes = offsetMinutes;
		this.message = message;
		this.createdBy = createdBy;
		this.createdAt = now;
	}

	/**
	 * Follows the event when it moves: an offset notification is recalculated, and one whose new time is already
	 * past becomes due immediately. Absolute ({@code AT}) notifications keep their time.
	 */
	public void reschedule(Instant newStart, Instant now) {
		if (status != NotificationStatus.SCHEDULED || offsetMinutes == null) {
			return;
		}
		Instant recalculated = newStart.minus(Duration.ofMinutes(offsetMinutes));
		this.sendAt = recalculated.isBefore(now) ? now : recalculated;
	}

	public void cancel() {
		if (status != NotificationStatus.SCHEDULED && status != NotificationStatus.SENDING) {
			throw new IllegalStateException("Only a notification that hasn't been sent can be cancelled.");
		}
		this.status = NotificationStatus.CANCELLED;
		this.claimedUntil = null;
	}

	public void claim(Instant now, Duration timeout) {
		this.status = NotificationStatus.SENDING;
		this.claimedUntil = now.plus(timeout);
	}

	public void markFannedOut(int count, Instant now) {
		this.status = NotificationStatus.SENT;
		this.recipientCount = count;
		this.sentAt = now;
		this.claimedUntil = null;
	}

	public UUID getId() {
		return id;
	}

	public Event getEvent() {
		return event;
	}

	public Group getGroup() {
		return group;
	}

	public NotificationKind getKind() {
		return kind;
	}

	public NotificationStatus getStatus() {
		return status;
	}

	public Instant getSendAt() {
		return sendAt;
	}

	public Integer getOffsetMinutes() {
		return offsetMinutes;
	}

	public String getMessage() {
		return message;
	}

	public Integer getRecipientCount() {
		return recipientCount;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public Instant getClaimedUntil() {
		return claimedUntil;
	}

	public Instant getSentAt() {
		return sentAt;
	}

	public long getVersion() {
		return version;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

}

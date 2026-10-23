package io.grouploom.backend.notification;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EventNotificationRepository extends JpaRepository<EventNotification, UUID> {

	/** Bounded by the domain (at most 10 scheduled, plus the ones already sent), so it isn't paged. */
	List<EventNotification> findAllByEventIdOrderBySendAtAsc(UUID eventId);

	Optional<EventNotification> findByIdAndEventIdAndGroupId(UUID id, UUID eventId, UUID groupId);

	long countByEventIdAndStatus(UUID eventId, NotificationStatus status);

	boolean existsByEventIdAndStatus(UUID eventId, NotificationStatus status);

	/**
	 * Claims up to {@code limit} due notifications (scheduled, or sending with an expired claim) for one fan-out.
	 * {@code SKIP LOCKED} keeps concurrent runs from picking the same rows. The version is bumped so an organizer
	 * unscheduling a notification that was just claimed fails instead of deleting it.
	 */
	@Transactional
	@Query(nativeQuery = true, value = """
			UPDATE event_notifications SET status = 'SENDING', claimed_until = :claimedUntil, version = version + 1
			WHERE id IN (
			    SELECT id FROM event_notifications
			    WHERE (status = 'SCHEDULED' AND send_at <= :now)
			       OR (status = 'SENDING' AND claimed_until < :now)
			    ORDER BY send_at
			    LIMIT :limit
			    FOR UPDATE SKIP LOCKED)
			RETURNING id
			""")
	List<UUID> claimDue(@Param("now") Instant now, @Param("claimedUntil") Instant claimedUntil,
			@Param("limit") int limit);

}

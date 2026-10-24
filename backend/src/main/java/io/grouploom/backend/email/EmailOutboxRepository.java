package io.grouploom.backend.email;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

public interface EmailOutboxRepository extends JpaRepository<EmailOutboxEntry, UUID> {

	/**
	 * Claims up to {@code limit} due rows (pending, or sending with an expired claim) for one
	 * dispatcher. {@code SKIP LOCKED} keeps concurrent dispatchers from picking the same rows.
	 */
	@Transactional
	@Query(nativeQuery = true, value = """
			UPDATE email_outbox SET status = 'SENDING', claimed_until = :claimedUntil
			WHERE id IN (
			    SELECT id FROM email_outbox
			    WHERE (status = 'PENDING' AND next_attempt_at <= :now)
			       OR (status = 'SENDING' AND claimed_until < :now)
			    ORDER BY next_attempt_at
			    LIMIT :limit
			    FOR UPDATE SKIP LOCKED)
			RETURNING id
			""")
	List<UUID> claimDue(@Param("now") Instant now, @Param("claimedUntil") Instant claimedUntil,
			@Param("limit") int limit);

	boolean existsByMemberIdAndKindAndCreatedAtAfterAndStatusNot(UUID memberId, EmailKind kind, Instant since,
			EmailStatus status);

	/** Members that got a non-failed email of this kind after {@code since}. */
	@Query("""
			select distinct e.member.id from EmailOutboxEntry e
			where e.member.id in :memberIds and e.kind = :kind and e.createdAt > :since
			  and e.status <> io.grouploom.backend.email.EmailStatus.FAILED
			""")
	Set<UUID> findMemberIdsEmailedSince(@Param("memberIds") Collection<UUID> memberIds,
			@Param("kind") EmailKind kind, @Param("since") Instant since);

	@Query(nativeQuery = true, value = """
			SELECT DISTINCT ON (member_id) member_id AS memberId, status AS status,
			       sent_at AS sentAt, created_at AS createdAt
			FROM email_outbox
			WHERE group_id = :groupId AND member_id IN (:memberIds) AND kind = :kind
			ORDER BY member_id, created_at DESC, id DESC
			""")
	List<LatestEmailStatus> findLatestStatus(@Param("groupId") UUID groupId,
			@Param("memberIds") Collection<UUID> memberIds, @Param("kind") String kind);

	@EntityGraph(attributePaths = { "member", "group", "requestedBy", "event", "event.eventType", "notification" })
	Optional<EmailOutboxEntry> findWithDetailsById(UUID id);

	/**
	 * Queues one email per member who should hear about an event of this type: not paused, and not opted out of
	 * the type. Reminders also skip members who answered that they can't go. One set-based statement, so a large group needs no per-member loop. The unique index on
	 * (notification_id, member_id) makes a repeat a no-op. Returns how many rows were inserted.
	 */
	@Modifying
	@Transactional
	@Query(nativeQuery = true, value = """
			INSERT INTO email_outbox (id, group_id, member_id, requested_by_user_id, kind, status, attempts,
			                          next_attempt_at, event_id, notification_id, created_at)
			SELECT gen_random_uuid(), m.group_id, m.id, CAST(:requestedBy AS uuid), :kind, 'PENDING', 0,
			       :now, :eventId, :notificationId, :now
			FROM members m
			LEFT JOIN member_preferences p ON p.member_id = m.id
			WHERE m.group_id = :groupId
			  AND p.invitations_paused_at IS NULL
			  AND NOT EXISTS (SELECT 1 FROM member_event_type_opt_outs o
			                  WHERE o.member_id = m.id AND o.event_type_id = :eventTypeId)
			  AND (CAST(:kind AS text) <> 'EVENT_REMINDER' OR NOT EXISTS (
			           SELECT 1 FROM attendances a
			           WHERE a.event_id = :eventId AND a.member_id = m.id AND a.response = 'NOT_GOING'))
			ON CONFLICT DO NOTHING
			""")
	int fanOutInvitation(@Param("notificationId") UUID notificationId, @Param("eventId") UUID eventId,
			@Param("groupId") UUID groupId, @Param("eventTypeId") UUID eventTypeId, @Param("kind") String kind,
			@Param("requestedBy") UUID requestedBy, @Param("now") Instant now);

	/**
	 * Queues a cancellation for every member who was actually emailed an invitation or reminder for the event,
	 * whatever their preferences are now. Members deleted since then have no outbox rows left.
	 */
	@Modifying
	@Transactional
	@Query(nativeQuery = true, value = """
			INSERT INTO email_outbox (id, group_id, member_id, requested_by_user_id, kind, status, attempts,
			                          next_attempt_at, event_id, notification_id, created_at)
			SELECT gen_random_uuid(), :groupId, s.member_id, CAST(:requestedBy AS uuid), 'EVENT_CANCELLATION', 'PENDING', 0,
			       :now, :eventId, :notificationId, :now
			FROM (SELECT DISTINCT member_id FROM email_outbox
			      WHERE event_id = :eventId AND group_id = :groupId AND status = 'SENT'
			        AND kind IN ('EVENT_INVITATION', 'EVENT_REMINDER')) s
			ON CONFLICT DO NOTHING
			""")
	int fanOutCancellation(@Param("notificationId") UUID notificationId, @Param("eventId") UUID eventId,
			@Param("groupId") UUID groupId, @Param("requestedBy") UUID requestedBy, @Param("now") Instant now);

	/** Delivery counts for all of an event's notifications in one query. */
	@Query("""
			select new io.grouploom.backend.email.DeliveryCount(e.notification.id, e.status, count(e))
			from EmailOutboxEntry e
			where e.notification.id in :notificationIds
			group by e.notification.id, e.status
			""")
	List<DeliveryCount> countByNotification(@Param("notificationIds") Collection<UUID> notificationIds);

	/** Members a notification of this kind would reach right now: the same conditions as {@link #fanOutInvitation}. */
	@Query(nativeQuery = true, value = """
			SELECT count(*) FROM members m
			LEFT JOIN member_preferences p ON p.member_id = m.id
			WHERE m.group_id = :groupId
			  AND p.invitations_paused_at IS NULL
			  AND NOT EXISTS (SELECT 1 FROM member_event_type_opt_outs o
			                  WHERE o.member_id = m.id AND o.event_type_id = :eventTypeId)
			  AND (CAST(:kind AS text) <> 'EVENT_REMINDER' OR NOT EXISTS (
			           SELECT 1 FROM attendances a
			           WHERE a.event_id = :eventId AND a.member_id = m.id AND a.response = 'NOT_GOING'))
			""")
	int countEligibleRecipients(@Param("groupId") UUID groupId, @Param("eventTypeId") UUID eventTypeId,
			@Param("eventId") UUID eventId, @Param("kind") String kind);

	@Query(nativeQuery = true, value = "SELECT count(*) FROM members WHERE group_id = :groupId")
	long countMembers(@Param("groupId") UUID groupId);

}

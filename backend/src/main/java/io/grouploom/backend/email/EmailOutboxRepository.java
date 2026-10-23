package io.grouploom.backend.email;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
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

	@EntityGraph(attributePaths = { "member", "group", "requestedBy" })
	Optional<EmailOutboxEntry> findWithDetailsById(UUID id);

}
